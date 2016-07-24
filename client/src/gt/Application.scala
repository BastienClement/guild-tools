package gt

import gt.component.GtTest
import gt.service.roster.User
import org.scalajs.dom.raw.HTMLSpanElement
import org.scalajs.dom.{Event, MouseEvent, NodeListOf}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import scala.util.{Failure, Success}
import util.Global._
import util.implicits._
import util.{Delay, Settings}
import xuen.Loader
import xuen.expr.PipesRegistry

/** The main GuildTools application object */
object Application extends js.JSApp {
	/** The logged in application user */
	def user: User = _user
	private[gt] var _user: User = _

	/** Elements to load when application is booting */
	private val coreElements = Seq(
		GtTest//, GtApp
	)

	/** Application entry point */
	def main(): Unit = {
		console.log("GuildTools Client 7.0")
		console.log("[BOOT] Loading application...")

		document.addEventListener("contextmenu", preventRightClick _)
		dynamic.GuildTools = this.asInstanceOf[js.Any]

		// Load pipes definitions
		PipesRegistry.load(pipes.Common)

		val coreLoaded = Future.sequence(Seq(
			Loader.loadLess("/assets/less/guildtools.less") andThen {
				case Success(_) => console.log("[BOOT] Main stylesheet loaded")
			},
			Future.sequence(coreElements.map(e => e.load())) andThen {
				case Success(_) => console.log("[BOOT] Core components loaded")
			}
		))

		(for {
			url <- Loader.fetch("/api/socket_url")
			connected <- {
				console.log(s"[BOOT] GTP3 server is: $url")
				Server.connect(url)
			}
			authenticated <- {
				console.log("[BOOT] Authenticating...")
				Future {}
			}
			core <- coreLoaded
			ready <- {
				console.log("[BOOT] Application ready")
				stopSpinner()
			}
			loader <- {
				document.body.classList.add("no-loader")
				document.body.classList.add("with-background")
				if (Settings.`loading.fast` is true) Future.successful(())
				else Delay(1100.millis)
			}
			init = {
				document.body.classList.add("app-loader")
				for (_ <- Delay(2.second)) {
					for (loader <- document.querySelectorAll("#loader")) {
						loader.parentNode.removeChild(loader)
					}
				}
				val app = document.createElement("gt-test")
				app.setAttribute("name", "Blash")
				document.body.appendChild(app)
			}
		} yield {}).andThen {
			case Success(_) => console.log("[BOOT] Loading successful")
			case Failure(e) => console.error("[BOOT] Loading failed: " + formatException(e))
		}
	}

	def stopSpinner(): Future[_] = {
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

	/** Prevent right click if shift key is not pressed */
	def preventRightClick(e: MouseEvent) = {
		if (!e.shiftKey) e.preventDefault()
	}
}
