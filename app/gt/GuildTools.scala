package gt

import akka.actor.ActorSystem
import com.google.inject.{Inject, Singleton}
import java.io.File
import java.nio.file.{FileSystems, StandardWatchEventKinds => SWEK}
import play.api.db.slick.DatabaseConfigProvider
import play.api.inject.ApplicationLifecycle
import play.api.libs.ws.WSClient
import play.api._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._
import slick.driver.JdbcProfile
import utils.CacheCell

object GuildTools {
	// Server stats
	val serverName = "GuildTools-6.0"
	val serverVersion = CacheCell(15.minutes) { "git rev-parse HEAD".!!.trim }
	val serverStart = Platform.currentTime
	def serverUptime = Platform.currentTime - serverStart

	// Workaround for dumb and useless DI requirement
	private[gt] var self_ref: GuildTools = null

	lazy val self = synchronized {
		//noinspection LoopVariableNotUpdated
		if (self_ref == null) wait(5000)
		if (self_ref == null) throw new Exception("Failed to load current Application")
		self_ref
	}

	lazy val env = self.env
	lazy val conf = self.conf
	lazy val system = self.system
	lazy val ws = self.ws
	lazy val db = self.dbc.get[JdbcProfile].db

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
	println("instantiating...")

	GuildTools.synchronized {
		// Leak this instance
		Logger.info("Starting GuildTools server...")
		GuildTools.self_ref = this
		GuildTools.notifyAll()
	}
	println("instantiated...")

	env.mode match {
		case Mode.Dev =>
			setupTypescriptCompiler()

		case Mode.Prod =>
			setupCharacterRefresher()
	}

	def stopHook(fn: => Unit): Unit = lifecycle.addStopHook(() => Future.successful(fn))

	def setupTypescriptCompiler(): Unit = {
		val path = conf.getString("dev.path").get
		val pwd = conf.getString("dev.dir").get
		val tsc = conf.getString("dev.tsc").get
		val node = conf.getString("dev.node").getOrElse("node")
		val watch = conf.getBoolean("dev.tsc.watch").getOrElse(true)

		if (watch) {
			val logger = ProcessLogger { line =>
				Logger.info("[TSC]: " + line)
			}

			Logger.info("[TSC]: Starting Typescript compiler")
			val process = Process(Seq(node, tsc, "-w"), new File(s"$pwd/public/tools"), "PATH" -> path).run(logger)

			stopHook {
				Logger.info("[TSC]: Stopping Typescript compiler")
				process.destroy()
			}
		} else {
			val ws = FileSystems.getDefault.newWatchService()
			val watch_dir = FileSystems.getDefault.getPath(pwd, "public", "tools")
			watch_dir.register(ws, SWEK.ENTRY_CREATE, SWEK.ENTRY_DELETE, SWEK.ENTRY_MODIFY)

			val thread = new Thread {
				def compile() = {
					println("TSC   - Compiling Typescript files")
					val logger = ProcessLogger { line => println("TSC   - " + line) }
					val process = Process(Seq(node, tsc), new File(s"$pwd/public/tools"), "PATH" -> path).run(logger)
					process.exitValue()
					println("TSC   - Done")
				}

				override def run() = {
					compile()
					var running = true
					while (running) {
						try {
							val wk = ws.take()

							wk.pollEvents()
							compile()
							wk.pollEvents()

							if (!wk.reset()) {
								running = false
								println("TSC   - Key has been unregistered")
							}
						} catch {
							case _: InterruptedException => running = false
						}
					}
				}
			}

			println("TSC   - Starting Typescript compiler")
			thread.start()

			stopHook {
				println("TSC   - Stopping Typescript compiler")
				thread.interrupt()
				thread.join()
			}
		}
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
