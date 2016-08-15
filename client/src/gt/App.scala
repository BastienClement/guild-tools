package gt

import boopickle.DefaultBasic._
import gt.component.app.GtApp
import gt.component.widget.floating.GtFloatingPlaceholder
import gt.service.RosterService
import model.User
import org.scalajs.dom.raw.HTMLSpanElement
import org.scalajs.dom.{Event, MouseEvent, NodeListOf, window}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success}
import util.Global._
import util.annotation.data
import util.implicits._
import util.{Delay, Settings}
import xuen.Loader
import xuen.expr.PipesRegistry

/** The main GuildTools application object */
@data object App extends js.JSApp {
	/** The root GtApp instance */
	var root: Option[GtApp] = None

	/** The logged in application user */
	var user: User = null

	/** Elements to load when application is booting */
	private val coreElements = Seq(GtApp, GtFloatingPlaceholder)

	/** Application entry point */
	def main(): Unit = {
		console.log("GuildTools Client 7.0")
		console.log("[BOOT] Loading application...")

		document.addEventListener("contextmenu", (e: MouseEvent) => { if (!e.shiftKey) e.preventDefault() })
		dynamic.GuildTools = this.asInstanceOf[js.Any]

		// Load pipes definitions
		PipesRegistry.load(Pipes)

		val coreLoaded = Future.sequence(Seq(
			Loader.loadLess("/assets/less/guildtools.less") andThen {
				case Success(_) => console.log("[BOOT] Main stylesheet loaded")
			},
			Future.sequence(coreElements.map(e => e.load())) andThen {
				case Success(_) => console.log("[BOOT] Core components loaded")
			}
		))

		(for {
			url <- Loader.fetch("/api/socket_url").map(normalizeWebsocketUrl)
			connected <- {
				console.log(s"[BOOT] GTP3 server is: $url")
				Server.connect(url)
			}
			authenticated <- {
				console.log("[BOOT] Authenticating...")
				Server.openChannel("auth").flatMap { channel =>
					channel.request("auth", Settings.`auth.session`: String).as[User].map(u => (u, channel))
				}.recover {
					case e: Throwable =>
						window.location.href = "/unauthorized"
						throw new Exception("Unauthorized")
				}.map {
					case (null, _) =>
						window.location.href = window.dyn.sso_url().as[String]
						throw new Exception("Unauthenticated")

					case (u, channel) =>
						user = u
						channel.close()
				}
			}
			roster_task = {
				RosterService.acquire()
				RosterService.loadRoster()
			}
			core <- coreLoaded
			ready <- {
				console.log("[BOOT] Application ready")
				RosterService.acquire()
				stopSpinner()
			}
			loader <- {
				document.body.classList.add("no-loader")
				document.body.classList.add("with-background")
				if (Settings.`loading.fast`) Future.successful(())
				else Delay(1100.millis)
			}
			roster_ready <- roster_task
			init = {
				document.body.classList.add("app-loader")

				for (_ <- Delay(2.second)) {
					for (loader <- document.querySelectorAll("#loader")) {
						loader.parentNode.removeChild(loader)
					}
				}

				val titlebar = document.getElementById("loader-titlebar")
				titlebar.parentNode.removeChild(titlebar)

				val app = document.createElement("gt-app")
				document.body.appendChild(app)
				root = Some(app.asInstanceOf[GtApp])

				Router.start()
			}
		} yield {}).andThen {
			case Success(_) => console.log("[BOOT] Loading successful")
			case Failure(e) => console.error("[BOOT] Loading failed: " + formatException(e))
		}
	}

	private def stopSpinner(): Future[_] = {
		if (Settings.`loading.fast` is true) {
			Future.successful(())
		} else {
			val promise = Promise[Unit]()
			val last_dot = document.querySelector("#loader .spinner b:last-child").as[HTMLSpanElement]

			var listener: js.Function1[Event, Unit] = null
			listener = (_: Event) => {
				last_dot.removeEventListener("animationiteration", listener)
				val dots = document.querySelectorAll("#loader .spinner b").as[NodeListOf[HTMLSpanElement]]
				for (dot <- dots) {
					dot.style.animationIterationCount = "1"
				}
				promise.success(())
				()
			}

			last_dot.addEventListener("animationiteration", listener)
			promise.future.flatMap(_ => Delay(500.millis))
		}
	}

	/** Normalizes the WebSocket url */
	private def normalizeWebsocketUrl(start: String): String = {
		var url = start
		if (document.location.protocol == "https:")
			url = url.replaceFirst("^ws:", "wss:")
		for (key <- Seq("hostname", "port", "host"))
			url = url.replace("$" + key, dynamic.location.selectDynamic(key).asInstanceOf[String])
		url
	}

	/** Formats an exception stack trace */
	def formatException(e: Throwable): String = {
		val (message, trace, cause) = e match {
			case js: js.JavaScriptException =>
				(js.toString, Some(js.exception.dyn.stack.split("\n").slice(1).join("\n").as[String]), None)
			case jvm: Throwable =>
				(jvm.toString, Some(jvm.getStackTrace.map(t => s"\t$t").mkString("\n")), Option(jvm.getCause))
		}

		val trace_txt = trace.map(t => "\n" + t).getOrElse("")
		val cause_txt = cause.map("\nCaused by " + formatException(_)).getOrElse("")

		s"$message$trace_txt$cause_txt"
	}

	/** The server version changed */
	def serverVersionChanged(): Unit = {
		???
	}
}
