package xuen.rx

import scala.collection.mutable
import scala.language.implicitConversions
import scala.scalajs.js.annotation.JSExport
import scala.util.DynamicVariable

/**
  * A reactive value.
  *
  * @tparam T the type of this reactive value
  */
trait Rx[+T] {
	// Status flags
	private[this] var haveChildren = false
	private[this] var haveObservers = false

	/** Children of this reactive value */
	private[this] lazy val children = {
		haveChildren = true
		mutable.Map[Expr[_], Int]()
	}

	/** Observers bound to this reactive value */
	private[this] lazy val observers = {
		haveObservers = true
		mutable.Set[Obs]()
	}

	/** Must be overriden to return the current value of this reactive value */
	private[rx] def value: T

	/** Extracts the current value and register child expression */
	@JSExport("get")
	protected[rx] final def get(): T = {
		Rx.enclosing.value match {
			case null => // No enclosing expression
			case child => children.put(child, child.invalidateKey)
		}
		value
	}

	/** Invalidate this reactive values and all its children */
	def invalidate(): Unit = Rx.atomically {
		// Invalidate children, if any
		if (haveChildren) {
			for ((child, key) <- children if child.invalidateKey == key) {
				child.invalidate()
			}
			children.clear()
		}

		// Enqueue observers, if any
		if (haveObservers) {
			for (observer <- observers if Rx.observersSet.add(observer)) {
				Rx.observersStack.push(observer)
			}
		}
	}

	/** Combines this reactive value with another one */
	def ~+[U >: T] (rhs: Rx[U]): Rx[U] = {
		var a: T = this
		var b: U = rhs
		Rx {
			val av: T = this
			val bv: U = rhs
			var res = b
			if (a != av) {
				a = av
				res = a
			}
			if (b != bv) {
				b = bv
				res = b
			}
			res
		}
	}

	/** Combines this reactive value with a mapper function */
	def ~[U] (mapper: T => U): Rx[U] = Rx { mapper(this) }

	/** Attaches this reactive value to an observer */
	def ~> (observer: Obs): this.type = {
		observers.add(observer)
		value
		this
	}

	/** Eagerly attaches this reactive value to an observer */
	def ~>> (observer: Obs): this.type = {
		this ~> observer
		observer.trigger()
		this
	}

	/** Detaches this reactive value from an observer */
	def ~!> (observer: Obs): this.type = {
		observers.remove(observer)
		this
	}

	final def ~> (handler: T => Unit): this.type = this ~> Obs(handler(this))
	final def ~>> (handler: T => Unit): this.type = this ~>> Obs(handler(this))


	/** Extracts the current value of the reactive value */
	final def ! : T = get()

	override def toString: String = s"Rx@${ Integer.toHexString(hashCode) }[$value]"
}

object Rx {
	/** Constructs a new reactive value wrapping the given expression */
	def apply[T](generator: => T): Rx[T] = new Expr(() => generator)

	/** Implicitly extracts the value from this reactive value */
	implicit def extractor[T](rx: Rx[T]): T = rx.get()

	/** Implicitly wraps a value in a reactive value */
	implicit def wrapper[T](value: => T): Rx[T] = new Expr(() => value)

	/** Stack of enclosing reactive value used to automatically bind children */
	private[rx] val enclosing = new DynamicVariable[Expr[_]](null)

	/** Whether the current atomically block is the top-level one */
	private[rx] var topLevel = true

	/** The stack of observers to notify once the atomically block exits */
	private[rx] val observersStack = mutable.Stack[Obs]()

	/** The set of observers already in the stacks */
	private[rx] val observersSet = mutable.Set[Obs]()

	/** Executes a mutator block atomically */
	def atomically[T](block: => T): T = {
		val isTopLevel = topLevel
		if (isTopLevel) topLevel = false
		val res = block
		if (isTopLevel) {
			while (observersStack.nonEmpty) {
				val observer = observersStack.pop()
				observersSet.remove(observer)
				observer.trigger()
			}
			topLevel = true
		}
		res
	}
}
