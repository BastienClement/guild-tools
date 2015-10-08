import play.libs.Akka
import scala.concurrent.{Future, ExecutionContext}
import scala.language.implicitConversions

package object reactive {
	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")

	def AsFuture[T](body: => T): Future[T] = {
		try {
			Future.successful(body)
		} catch {
			case e: Throwable => Future.failed(e)
		}
	}
}
