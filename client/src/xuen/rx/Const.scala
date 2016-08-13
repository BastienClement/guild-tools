package xuen.rx

/**
  * A reactive constant.
  */
class Const[+T] protected[rx] (v: T) extends Rx[T] {
	private[rx] def value: T = v
	override def invalidate(): Unit = {}
	override def toString: String = s"Const@${ Integer.toHexString(hashCode) }[$v]"
}

object Const {
	def apply[T](v: T): Const[T] = new Const(v)
}
