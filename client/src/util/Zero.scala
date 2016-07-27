package util

trait Zero[T] {
	val zero: T
}

object Zero {
	case class StaticZero[T](zero: T) extends Zero[T]

	implicit val boolean: Zero[Boolean] = StaticZero(false)
	implicit val byte: Zero[Byte] = StaticZero(0: Byte)
	implicit val short: Zero[Short] = StaticZero(0: Short)
	implicit val int: Zero[Int] = StaticZero(0)
	implicit val long: Zero[Long] = StaticZero(0L)
	implicit val float: Zero[Float] = StaticZero(0.0f)
	implicit val double: Zero[Double] = StaticZero(0.0)
	implicit val char: Zero[Char] = StaticZero('\u0000')
	implicit val anyref: Zero[AnyRef] = StaticZero(null)
	@inline implicit def ref[T <: AnyRef]: Zero[T] = anyref.asInstanceOf[Zero[T]]
}
