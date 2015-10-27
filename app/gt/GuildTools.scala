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
	Play.mode match {
		case Mode.Dev =>
			setupTypescriptCompiler()

		case Mode.Prod =>
			setupCharacterRefresher()
	}

	def stopHook(fn: => Unit): Unit = lifecycle.addStopHook(() => Future.successful(fn))

	def setupTypescriptCompiler(): Unit = {
		val path = Play.configuration.getString("dev.path").get
		val pwd = Play.configuration.getString("dev.dir").get
		val tsc = Play.configuration.getString("dev.tsc").get

		println("TSC   - Starting Typescript compiler...")
		val process = Process(Seq(tsc, "-w"), new File(s"$pwd/public/tools"), "PATH" -> path) run ProcessLogger { line =>
			println("TSC   - " + line)
		}

		stopHook { process.destroy() }
	}

	def setupCharacterRefresher(): Unit = {
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
}
