package gt.component.profile

import gt.component.GtHandler
import gt.component.widget._
import gt.component.widget.form.{GtButton, GtForm, GtInput}
import gt.service.Profile
import model.Toon
import org.scalajs.dom.raw.HTMLImageElement
import org.scalajs.dom.{Event, document}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import util.implicits.NodeListSeq
import util.jsannotation.js
import xuen.Component
import xuen.rx.{Rx, Var}

object ProfileAddChar extends Component[ProfileAddChar](
	selector = "profile-add-char",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtDialog, GtBox, GtButton, GtForm, GtInput)
)

@js class ProfileAddChar extends GtHandler {
	val profile = service(Profile)
	val owner = attribute[Int]

	val server = Var("")
	val name = Var("")
	val role = Var("")
	val toon = Var(null: Toon)
	val loadDone = Var(false)
	val inFlight = Var(false)

	val hasToon = toon ~ (_ != null)
	val canLoad = Rx { server.trim.nonEmpty && name.trim.nonEmpty && !loadDone }
	val confirmDisabled = Rx { toon.! == null || inFlight }

	def loadToonImage(thumbnail: String): Future[Unit] = {
		val promise = Promise[Unit]()

		val img = document.createElement("img").asInstanceOf[HTMLImageElement]
		img.src = "https://eu.battle.net/static-render/eu/" + thumbnail.replace("avatar", "profilemain")
		child.background.appendChild(img)

		// Image loaded successfully
		img.addEventListener("load", (e: Event) => {
			img.classList.add("loaded")
			promise.success(())
		})

		// Ignore image loading failed
		img.addEventListener("error", (e: Event) => promise.success(()))

		promise.future
	}

	/** Removes old toon images */
	def removeToonImages(): Unit = {
		for (img <- child.background.querySelectorAll("img:not([default])")) {
			img.parentNode.removeChild(img)
		}
	}

	def load(): Unit = if (canLoad) {
		loadDone := true

		val input = child.as[GtInput].name
		input.error := null

		val process = for {
			available <- profile.isToonAvailable(server, name)
			_ = if (!available) throw new Exception("This character is already registered")
			toon <- profile.fetchToon(server, name)
			img <- loadToonImage(toon.thumbnail)
		} yield {
			this.toon := toon
			this.role := toon.role
		}

		for (e <- process.failed) {
			input.error := e.getMessage
			input.value := ""
			input.focus()
			loadDone := false
		}
	}

	def confirm(): Unit = if(!inFlight) {
		inFlight := true

		profile.registerToon(server, name, role, owner).andThen {
			case _ => inFlight := false
		}.onComplete {
			case Failure(e) =>
				val input = child.as[GtInput].name
				input.error := e.getMessage
				input.value := ""
				loadDone := false
				toon := null
				removeToonImages()
				input.focus()

			case Success(_) =>
				child.as[GtDialog].dialog.hide()
		}
	}

	def show() = {
		child.as[GtForm].form.reset()

		server := "sargeras"
		role := ""
		toon := null
		loadDone := false
		inFlight := false

		// Remove old backgrounds
		removeToonImages()

		// Show the dialog
		child.as[GtDialog].dialog.show()

		// Focus the server field
		child.name.focus()
	}
}
