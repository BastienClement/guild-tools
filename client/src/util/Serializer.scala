package util

import scala.util.Try

trait Serializer[T] {
	val default: T
	def write(value: T): Option[String]
	def read(value: Option[String]): Option[T]
	def read(value: Option[String], fallback: => T = default): T = read(value).getOrElse(fallback)
}

object Serializer {
	implicit val string = new Serializer[String] {
		val default: String = null
		def write(value: String): Option[String] = Some(value)
		def read(value: Option[String]): Option[String] = Some(value.orNull)
	}

	implicit val boolean = new Serializer[Boolean] {
		val default: Boolean = false
		def write(value: Boolean): Option[String] = if (value) Some("") else None
		def read(value: Option[String]): Option[Boolean] = Some(value.isDefined)
	}

	def numeric[T](unserializer: (String) => T)(implicit numeric: Numeric[T]) = new Serializer[T] {
		val default: T = numeric.zero
		def write(value: T): Option[String] = Some(value.toString)
		def read(value: Option[String]): Option[T] = value.map(v => Try(unserializer(v)).getOrElse(default))
	}

	implicit val byte = numeric(java.lang.Byte.parseByte)
	implicit val int = numeric(java.lang.Integer.parseInt)
	implicit val long = numeric(java.lang.Long.parseLong)
	implicit val float = numeric(java.lang.Float.parseFloat)
	implicit val double = numeric(java.lang.Double.parseDouble)

	val dummy = new Serializer[Any] {
		lazy val default: Any = throw new UnsupportedOperationException
		def write(value: Any): Option[String] = Some(value.toString)
		def read(value: Option[String]): Option[Any] = throw new UnsupportedOperationException
	}

	def forValue[T](value: T): Serializer[T] = (value match {
		case _: String => string
		case _: Boolean => boolean
		case _: Byte => byte
		case _: Int => int
		case _: Long => long
		case _: Float => float
		case _: Double => double
		case _ =>
			//console.error("Unknown type for Serializer: ", value.getClass.getName)
			dummy
	}).asInstanceOf[Serializer[T]]
}
