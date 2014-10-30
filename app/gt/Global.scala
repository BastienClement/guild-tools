package gt

import scala.concurrent.ExecutionContext
import scala.sys.process._
import play.libs.Akka

object Global {
	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")
	val serverVersion = "git rev-parse HEAD".!!.trim
}
