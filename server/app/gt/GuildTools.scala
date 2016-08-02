package gt

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import java.nio.file.{StandardWatchEventKinds => SWEK}
import play.api._
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._
import scala.util.Try
import utils.CacheCell

object GuildTools {
	// Server stats
	val serverName = "GuildTools-6.0"
	val serverVersion = CacheCell(15.minutes) {
		Try { "git rev-parse HEAD".!!.trim } getOrElse "{unavailable}"
	}
	val serverStart = Platform.currentTime
	def serverUptime = Platform.currentTime - serverStart

	// Workaround for dumb and useless DI requirement
	@Inject
	private[gt] var self: GuildTools = null

	lazy val env = self.env
	lazy val conf = self.conf
	lazy val system = self.system
	lazy val ws = self.ws

	lazy val prod = env.mode == Mode.Prod
	lazy val dev = env.mode == Mode.Dev
}

@Singleton
class GuildTools @Inject() (val lifecycle: ApplicationLifecycle,
                            val env: Environment,
                            val conf: Configuration,
                            val system: ActorSystem,
                            val ws: WSClient,
                            val dbc: DatabaseConfigProvider) {
	Logger.info("Starting GuildTools server...")

	env.mode match {
		case Mode.Dev =>
			//setupTypescriptCompiler()

		case Mode.Prod =>
			setupCharacterRefresher()
	}

	def stopHook(fn: => Unit): Unit = lifecycle.addStopHook(() => Future.successful(fn))

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
