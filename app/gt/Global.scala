package gt

import scala.sys.process._

object Global {
	val serverVersion = "git rev-parse HEAD".!!.trim

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
