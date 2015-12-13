package reactive

import java.util.concurrent.atomic.AtomicBoolean
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.ref.WeakReference
import scala.util.DynamicVariable

/**
  * A domain is an enclosing element for reactive variables and expressions.
  * Elements from one domain cannot interract with elements from another domain.
  *
  * The domain is passed implicitly when manipulating reactives items.
  * A implicit global domain is available as reactive.Implicits.globalDomain
  */
class Domain {
	/** The enclosing reactive expression stack. Used for automatic dependency binding */
	private[this] val enclosing = new DynamicVariable[Rx[_]](null)

	/** Atomically check if a given invalidation is top-level */
	private[this] val top_level = new AtomicBoolean(false)

	/** Pending observators stack */
	private[this] val pending_obs = mutable.Stack[Obs]()

	/** Set of pending observators, used to check for presence of the obs in the stack */
	private[this] val pending_set = mutable.Set[Obs]()

	/**
	  * Basic trait of a reactive value.
	  * @tparam T The type of the reactive value
	  */
	trait Rx[+T] {
		/** This Rx's domain */
		val domain = Domain.this

		/** An RxToolkit bound to this Rx's domain */
		private[this] val Rx = reactive.Rx(Domain.this)

		/** "Official" weak reference to this Rx object. */
		val weakRef = new WeakReference[Rx[T]](this)

		/**
		  * List of children of this reactive value.
		  * Children will be notifed when this object's value is invalidated.
		  * The mapped value indicate if the binding is weak and will be removed as soon
		  * as the invalidation is propagated.
		  */
		protected val children = TrieMap[WeakReference[Rx[_]], Boolean]()

		/**
		  * List of observers of this reactive value.
		  */
		protected val observers = TrieMap[WeakReference[Obs], Unit]()

		/**
		  * Returns the value of this reactive expression.
		  */
		protected def value: T

		/**
		  * Returns the value of this reactive value.
		  * Also checks for enclosing expression and automatic binding.
		  */
		final def apply(): T = {
			enclosing.value match {
				case null => // No enclosing expression
				case child => children.putIfAbsent(child.weakRef, true)
			}
			value
		}

		/**
		  * Invalidate this value.
		  * It will atomically invalidate every children and add observers into the stack.
		  * This process is based on a DFS to have the observer stack be topologically sorted.
		  */
		protected def invalidate(): Unit = Domain.this.atomically {
			// Invalidate children
			for ((child_ref, weak) <- children) {
				child_ref.get match {
					case None => children.remove(child_ref)
					case Some(child) =>
						child.invalidate()
						if (weak) children.remove(child_ref)
				}
			}

			// Register observers for later call
			for ((observer_ref, _) <- observers) {
				observer_ref.get match {
					case None => observers.remove(observer_ref)
					case Some(observer) =>
						if (!pending_set.contains(observer)) {
							pending_set.add(observer)
							pending_obs.push(observer)
						}
				}
			}
		}

		/**
		  * Binds an observer to this reactive value.
		  *
		  * @param observer The observer
		  * @return this
		  */
		private[reactive] def bindObserver(observer: Obs) = {
			observers.put(observer.weakRef, ())
			this
		}

		/**
		  * Binds a child Rx to this one.
		  *
		  * @param child The child to invalidate when this Rx is invalidated
		  * @param weak If the binding is weak, it will be removed after the first invalidation
		  * @return The parent Rx
		  */
		private[reactive] def bindChild(child: Rx[_], weak: Boolean = false) = {
			children.put(child.weakRef, weak)
			this
		}

		/**
		  * Binds this Rx to another one.
		  *
		  * @param parent The parent Rx that will notify this Rx when it is invalidated
		  * @param weak If the binding is weak, it will be removed after the first invalidation
		  * @return The child Rx
		  */
		private[reactive] def bindTo(parent: Rx[_], weak: Boolean = false) = {
			parent.bindChild(this)
			this
		}

		def map[U](f: T => U): Rx[U] = Rx { f(this()) } bindTo this
		def foreach(f: T => Unit): Obs = Rx.observe(this) { f(this()) }

		/**
		  * Merge other Rxs with this one, producing a new Rx that will produce every
		  * values produced by merged Rxs.
		  */
		def merge[U >: T](rx: Rx[U], rxs: Rx[U]*): Rx[U] = {
			val last = Rx.variable(rx())
			val observers = (rx +: rxs) map { r => Rx.observe(r) { last := r() } }
			last
		}
	}

	/**
	  * A reactive value based on an opaque expression return value.
	  * When evaluation the expression, access to other Rxs will automatically
	  * add this object as a child of the accessed one.
	  * The OpaqueRx will be weakly bound.
	  */
	class OpaqueRx[+T] private[reactive] (gen: () => T) extends Rx[T] {
		/**
		  * Current cached value.
		  */
		private[this] var current: Option[T] = None

		/**
		  * Compute this Rx's value.
		  */
		def value = current match {
			case Some(v) => v
			case None => Domain.this.synchronized {
				current match {
					case Some(v) => v
					case None => enclosing.withValue(this) {
						val result = gen()
						current = Some(result)
						result
					}
				}
			}
		}

		/**
		  * Invalidate the cached value.
		  */
		override def invalidate() = Domain.this.synchronized {
			if (current.isDefined) {
				current = None
				super.invalidate()
			}
		}
	}

	/**
	  * A reactive variable.
	  * A variable always hold a current value and can invalidate dependant expressions
	  * when its value changes.
	  */
	class Var[T] private[reactive] (initial: T) extends Rx[T] {
		protected[this] var current = initial
		def value = current

		def update(v: T) = {
			current = v
			invalidate()
		}

		def := (v: T) = update(v)
		def asRx: Rx[T] = this
	}

	/**
	  * An observer that can be notified when a reactive value changes.
	  */
	class Obs private[reactive] (handler: () => Unit) {
		/**
		  * Weak reference to self
		  */
		val weakRef = new WeakReference[Obs](this)

		/**
		  * Binds this observer to an Rx.
		  * @param rx The Rx to which this observer will be bound
		  * @return This observer
		  */
		private[reactive] def bindTo(rx: Rx[_]) = {
			rx.bindObserver(this)
			this
		}

		/**
		  * Trigger the observer, invoking its handler.
		  * @return This observer
		  */
		private[reactive] def trigger() = {
			handler()
			this
		}
	}

	/**
	  * Atomically executes an block on this domain.
	  * During the block execution, no observer will be notified of invalidations
	  * occuring in the domain. Once the block execution completes, every pending
	  * observers will be notified in a topologically-based order.
	  *
	  * @param block The block to execute
	  * @tparam T The return type of the block
	  * @return The return value of the block
	  */
	def atomically[T](block: => T): T = synchronized {
		val is_top_level = top_level.compareAndSet(false, true)
		val res = block
		if (is_top_level) {
			while (pending_obs.nonEmpty) {
				val observer = pending_obs.pop()
				pending_set.remove(observer)
				observer.trigger()
			}
			top_level.set(false)
		}
		res
	}
}
