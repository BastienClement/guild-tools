package utils

/**
  * A mix-in trait that removes stack traces from Throwables
  */
trait Stackless extends Throwable {
	override def fillInStackTrace(): Throwable = this
}

/**
  * A generic Exception without stack trace for improved performances
  */
class StacklessException(message: String, cause: Throwable) extends Exception(message, cause) with Stackless {
	def this(message: String) = this(message, null)
}

object StacklessException {
	@inline def apply(message: String, cause: Throwable = null) = new StacklessException(message, cause)
}
