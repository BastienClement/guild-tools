package gt

import scala.compat.Platform
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process._
import actors.Actors._
import models._
import models.mysql._
import play.libs.Akka
import utils._

object Global {
	implicit lazy val ExecutionContext: ExecutionContext = Akka.system.dispatchers.lookup("default-pool")
	val serverVersion = "git rev-parse HEAD".!!.trim

	// Char update job
	scheduler.schedule(15.seconds, 15.minutes) {
		val chars = RosterService.chars.values.toSeq.view
		chars.filter(c => c.active && !c.invalid && c.last_update < Platform.currentTime - 21600000)
			.sortBy(_.last_update.asc)
			.take(5)
			.map(_.id)
			.foreach(RosterService.refreshChar)
	}
}
