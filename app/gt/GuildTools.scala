package gt

import com.google.inject.Inject
import java.io.File
import play.api.Play.current
import play.api.inject.ApplicationLifecycle
import play.api.{Mode, Play}
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._
import utils.CacheCell

object GuildTools {
	// Server stats
	val serverName = "GuildTools-6.0"
	val serverVersion = CacheCell(15.minutes) { "git rev-parse HEAD".!!.trim }
	val serverStart = Platform.currentTime
	def serverUptime = Platform.currentTime - serverStart
}

class GuildTools @Inject() (lifecycle: ApplicationLifecycle) {
}
