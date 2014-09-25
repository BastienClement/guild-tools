package gt

import play.libs.Akka
import scala.concurrent.ExecutionContext

object Global {
	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")
}
