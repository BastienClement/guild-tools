package utils

import scala.concurrent.Future
import scala.language.implicitConversions

object Implicits {
	implicit def FutureBoxing[T](v: T): Future[T] = Future.successful(v)
	implicit def FutureBoxing[T](v: Option[T]): Future[T] = {
		try {
			Future.successful(v.get)
		} catch {
			case e: Throwable => Future.failed(e)
		}
	}

	implicit def OptionBoxing[T](v: T): Option[T] = Option(v)
}
