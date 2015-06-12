import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Await, Awaitable}
import scala.language.implicitConversions
import play.libs.Akka

package object reactive {
	final val Future = scala.concurrent.Future
	type Future[T] = scala.concurrent.Future[T]

	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")

	implicit class Awaiter[A](awaitable: Awaitable[A]) {
		def get(timeout: FiniteDuration): A = Await.result(awaitable, timeout)
		def get: A = get(5.seconds)
	}

	implicit def FutureWrapper[T](value: T): Future[T] = Future.successful(value)
}
