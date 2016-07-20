package xuen.rx

/**
  * A reactive expression.
  *
  * The given generator will be evaluated to compute an initial value for this
  * container. During evaluation, access to Rx variables are recorded and this
  * object is registrered as depending on these values. The generator must be
  * functionally pure and side-effects free.
  *
  * When one parent Rx value changes, this expression value will be invalidated
  * and recomputed.
  *
  * @param generate the value generator
  * @tparam T the type of the result of the generator
  */
class Expr[T] protected[rx] (generate: () => T) extends Rx[T] {
	/** The current value of this expression */
	private[this] var current: Option[T] = None

	/** Compute this expression value */
	private[rx] def value = current match {
		case Some(v) => v
		case None => Rx.enclosing.withValue(this) {
			val res = generate()
			current = Some(res)
			res
		}
	}

	/** Invalidates the cached value */
	override def invalidate(): Unit = {
		if (current.isDefined) {
			current = None
			super.invalidate()
		}
	}

	/** String representation */
	override def toString: String = s"Rx@${ Integer.toHexString(hashCode) }[$current]"
}
