package gt

import play.libs.Akka

import scala.concurrent.ExecutionContext
import scala.sys.process._

object Global {
	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")
	val serverVersion = "git rev-parse HEAD".!!.trim
}
