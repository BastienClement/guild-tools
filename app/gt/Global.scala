package gt

import scala.compat.Platform
import scala.sys.process._
import utils.CacheCell
import scala.concurrent.duration._

object Global {
	// Server stats
	val serverName = "GuildTools-6.0"
	val serverVersion = CacheCell(15.minutes) { "git rev-parse HEAD".!!.trim }
	val serverStart = Platform.currentTime
	def serverUptime = Platform.currentTime - serverStart

	// Char update job
	/*scheduler.schedule(1500.seconds, 15.minutes) {
		val chars = RosterService.chars.values.toSeq.view
		chars.filter(c => c.active && !c.invalid && c.last_update < Platform.currentTime - 21600000)
			.sortBy(_.last_update)
			.take(5)
			.map(_.id)
			.foreach(RosterService.refreshChar)
	}*/
}
