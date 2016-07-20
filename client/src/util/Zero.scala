package util

trait Zero[T] {
	val zero: T
}

object Zero {
	implicit val boolean = new Zero[Boolean] { val zero = false }
	implicit val byte = new Zero[Byte] { val zero = 0: Byte }
	implicit val short = new Zero[Short] { val zero = 0: Short }
	implicit val int = new Zero[Int] { val zero = 0 }
	implicit val long = new Zero[Long] { val zero = 0L }
	implicit val float = new Zero[Float] { val zero = 0.0f }
	implicit val double = new Zero[Double] { val zero = 0.0 }
	implicit val char = new Zero[Char] { val zero = '\u0000' }
	implicit val anyref = new Zero[AnyRef] { val zero = null }
	@inline implicit def ref[T <: AnyRef]: Zero[T] = anyref.asInstanceOf[Zero[T]]
}
