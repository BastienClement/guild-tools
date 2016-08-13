package xuen.rx

import scala.collection.mutable
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.scalajs.js.Date
import scala.scalajs.js.timers.setInterval
import scala.util.DynamicVariable
import xuen.rx.syntax.MonadicOps

/**
  * A reactive value.
  *
  * @tparam T the type of this reactive value
  */
trait Rx[+T] {
	// Status flags
	private[rx] var hasChildren = false
	private[rx] var hasObservers = false
	private[rx] var alive = true

	/** Children of this reactive value */
	private[rx] lazy val children = {
		hasChildren = true
		mutable.Map[Expr[_], Int]()
	}

	/** Observers bound to this reactive value */
	private[rx] lazy val observers = {
		hasObservers = true
		mutable.Set[Obs]()
	}

	/** Must be overriden to return the current value of this reactive value */
	private[rx] def value: T

	/** Extracts the current value and register child expression */
	protected[rx] final def get(): T = {
		if (!alive) {
			throw new IllegalStateException("Attempted to access a killed Rx value")
		}

		Rx.enclosing.value match {
			case null => // No enclosing expression
			case child => children.put(child, child.invalidateKey)
		}

		value
	}

	/** Invalidate this reactive values and all its children */
	protected[rx] def invalidate(): Unit = Rx.atomically {
		// Invalidate children, if any
		if (hasChildren) {
			for ((child, key) <- children if child.invalidateKey == key) {
				child.invalidate()
			}
			children.clear()
		}

		// Enqueue observers, if any
		if (hasObservers && alive) {
			for (observer <- observers if Rx.observersSet.add(observer)) {
				Rx.observersStack.push(observer)
			}
		}
	}

	/** Combines this reactive value with another one (merge) */
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

	/** Combines this reactive value with a mapper function (map) */
	def ~[U] (mapper: T => U): Rx[U] = this.map(mapper)

	/** Combines this reactive value with a mapper function (flatmap) */
	def ~![U] (mapper: T => Rx[U]): Rx[U] = this.flatMap(mapper)

	/** Attaches this reactive value to an observer */
	def ~> (observer: Obs): this.type = {
		if (!alive) {
			throw new IllegalStateException("Attempted to add an observer to a killed Rx value")
		}

		observers.add(observer)
		this
	}

	/** Eagerly attaches this reactive value to an observer */
	def ~>> (observer: Obs): this.type = {
		this ~> observer
		observer.trigger()
		this
	}

	/** Detaches this reactive value from an observer */
	def ~/> (observer: Obs): this.type = {
		observers.remove(observer)
		this
	}

	final def ~> (handler: T => Unit): this.type = this ~> Obs(handler(get()))
	final def ~>> (handler: T => Unit): this.type = this ~>> Obs(handler(get()))

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
	//implicit def wrapper[T](value: => T): Rx[T] = new Expr(() => value)
	implicit def wrapper[T](value: => T): Rx[T] = new Const(value)

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

	def tupled[A, B](a: Rx[A], b: Rx[B]): Rx[(A, B)] = Rx { (a.!, b.!) }
	def tupled[A, B, C](a: Rx[A], b: Rx[B], c: Rx[C]): Rx[(A, B, C)] = Rx { (a.!, b.!, c.!) }
	def tupled[A, B, C, D](a: Rx[A], b: Rx[B], c: Rx[C], d: Rx[D]): Rx[(A, B, C, D)] = Rx { (a.!, b.!, c.!, d.!) }
	def tupled[A, B, C, D, E](a: Rx[A], b: Rx[B], c: Rx[C], d: Rx[D], e: Rx[E]): Rx[(A, B, C, D, E)] = Rx { (a.!, b.!, c.!, d.!, e.!) }

	def kill(value: Rx[_]): Unit = {
		value.alive = false
		if (value.hasObservers) {
			for (obs <- value.observers) {
				value ~/> obs
			}
		}
		value.invalidate()
	}

	def invalidate(value: Rx[_]): Unit = value.invalidate()

	/** A time source that updates every minutes <<<< */
	lazy val time: Rx[Double] = {
		var clock = Var(Date.now())
		setInterval(1.minute) { clock := Date.now() }
		clock
	}
}
