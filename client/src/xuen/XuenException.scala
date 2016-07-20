package xuen

/**
  * An exception that occured when performing Xuen-related operations.
  *
  * @param message the exception message
  */
case class XuenException(private val message: String) extends Exception(message)
