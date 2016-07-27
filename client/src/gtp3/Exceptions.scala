package gtp3

case class GTP3Error(private val message: String, private val cause: Throwable = null)
		extends Exception(message, cause)

case class RequestError(message: String, code: Int, stack: String) extends Exception(message)

case object DuplicatedFrame extends Exception
