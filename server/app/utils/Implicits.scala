package utils

import reactive.ExecutionContext
import scala.concurrent.Future
import scala.language.implicitConversions

/**
  * Useful generic implicits
  */
object Implicits {
	/**
	  * Implicitly transform a T to a Future[T].
	  *
	  * @param value Value to box into a Future
	  * @tparam T Type of the value
	  * @return A Future[T] resolved with the given value
	  */
	implicit def FutureBoxing[T](value: T): Future[T] = Future.successful(value)

	/**
	  * Implicitly transform a Throwable to a failed Future[T].
	  */
	implicit def FutureBoxing[T](e: Throwable): Future[T] = Future.failed(e)

	/**
	  * Implicitly transform an Option[T] to a Future[T].
	  * None[T] is wrapped into a failed Future[T]
	  *
	  * @param value Optional value to box into a Future
	  * @tparam T Type of the value
	  * @return A Future[T] resolved with the given value or a failed
	  *         Future[T] if None was given.
	  */
	implicit def FutureBoxing[T](value: Option[T]): Future[T] = {
		try {
			Future.successful(value.get)
		} catch {
			case e: Throwable => Future.failed(e)
		}
	}

	/**
	  * Implicitly transform a T to an Option[T].
	  *
	  * @param value Value to box into an Option
	  * @tparam T Type of the value
	  * @return Some[T] with the given value or None if null was given.
	  *         Actually, the result of calling Option(value).
	  */
	implicit def OptionBoxing[T](value: T): Option[T] = Option(value)

	/**
	  * Utility methods for Futures
	  */
	implicit class FutureUtils[T](val future: Future[T]) extends AnyVal {
		/**
		  * Replace this future's failure exception with one containing the
		  * given message. The original exception is preserved as the cause
		  * of the new one.
		  *
		  * @param msg Message of the new exception
		  */
		@inline def otherwise(msg: String): Future[T] = future.recoverWith {
			case e =>
				println(e)
				Future.failed(new StacklessException(msg, e))
		}
	}
}
