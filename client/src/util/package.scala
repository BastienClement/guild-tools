import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import util.Global.setTimeout

package object util {
	def After(duration: FiniteDuration): Future[Unit] = After(duration, ())
	def After[T](duration: FiniteDuration, value: T): Future[T] = {
		val promise = Promise[T]()
		setTimeout(duration.toMillis) { promise.success(value) }
		promise.future
	}

	def Delay(duration: FiniteDuration): Future[Unit] = After(duration, ())

	final def stub[T]: T = null.asInstanceOf[js.Dynamic].asInstanceOf[T]
}
