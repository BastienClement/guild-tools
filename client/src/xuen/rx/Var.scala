package xuen.rx

import scala.language.implicitConversions
import scala.scalajs.js.annotation.JSExport

/**
  * A reactive varibale, whose value can be modified.
  *
  * @param initial the initial value of this variable
  * @tparam T the type of this reactive value
  */
class Var[T] protected (initial: T) extends Rx[T] {
	/** Current value of this reactive variable */
	private[this] var current = initial

	/** Extracts the current value of this variable */
	private[rx] def value: T = current

	/**
	  * Change the value in this reactive variable.
	  *
	  * This operation is a no-op if the new value is the same as the old one.
	  *
	  * @param value the new value of this reactive variable
	  * @return the updated reactive variable
	  */
	@JSExport("set")
	def := (value: T): this.type = {
		if (current != value) {
			current = value
			invalidate()
		}
		this
	}

	def ~= (fn: T => T): this.type = {
		this := fn(current)
	}

	/** Binds this variable to the given reactive value */
	def <~ (rx: Rx[T]): this.type = {
		rx ~>> { ref => this := ref }
		this
	}

	/** Binds this variable to the given reactive expression */
	final def <~ (expr: => T): this.type = this <~ Rx(expr)
	override def toString = s"Var@${ Integer.toHexString(hashCode) }[$current]"
}

object Var {
	implicit def wrapper[T](value: T): Var[T] = Var(value)
	def apply[T](value: T): Var[T] = new Var(value)
}
