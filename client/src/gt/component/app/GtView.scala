package gt.component.app

import gt.component.{GtHandler, View}
import gt.{App, Router}
import org.scalajs.dom._
import org.scalajs.dom.raw.HTMLElement
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import util.implicits._
import util.jsannotation.js
import xuen.Component

object GtView extends Component[GtView](
	selector = "gt-view",
	templateUrl = "/assets/imports/app.html"
)

@js class GtView extends GtHandler {
	private[this] var currentLayer = 0
	private[this] var currentNode: Element = null
	private[this] var currentArgs: Seq[(String, String)] = Nil
	private[this] var currentSelector: String = null

	Router.onUpdate ~> { case (_, args, view) => update(args, view) }

	private def update(args: Seq[(String, String)], view: View): Unit = {
		if (view.selector != currentSelector) {
			document.body.classList.add("app-loader")
		}

		// If the view is the same and is sticky, do not remove the current
		// element but update attributes values
		if (view.selector == currentSelector && view.sticky) {
			var map = args.toMap
			for ((name, _) <- currentArgs.toMap) map.get(name) match {
				case Some(value) =>
					currentNode.setAttribute(name, value)
					map = map - name
				case None =>
					currentNode.removeAttribute(name)
			}
			for ((name, value) <- args) {
				currentNode.setAttribute(name, value)
			}

			currentArgs = args
			return
		}

		currentNode = null

		// Remove every element children
		var tasks: Seq[Future[_]] = for (child <- children) yield {
			val promise = Promise[Unit]()
			child.classList.remove("active")
			child.addEventListener("transitionend", (e: Event) => {
				removeChild(child)
				promise.success(())
			})
			promise.future
		}

		// Add the element loading task
		tasks = tasks :+ view.component.load()

		// Prevent navigation during the transition
		Router.lock()

		// Wait until everything is ready to create the view element
		Future.sequence(tasks.asInstanceOf[Seq[Future[Unit]]]).onComplete {
			case Failure(e) =>
				window.console.error("Failure to load view", App.formatException(e))

			case Success(_) =>
				// Hide loader
				document.body.classList.remove("app-loader")

				// Construct the element
				val node = document.createElement(view.selector).asInstanceOf[HTMLElement]
				for ((name, value) <- args if value != null) node.setAttribute(name, value)

				// Ensure the new element is over older ones no matter what
				currentLayer += 1
				node.style.zIndex = currentLayer.toString

				currentNode = node
				currentArgs = args
				currentSelector = view.selector

				appendChild(node)

				// Add active class two frames from now
				// (don't ask why, I can't remember)
				window.requestAnimationFrame((_: Double) => {
					window.requestAnimationFrame((_: Double) => {
						node.classList.add("active")
					})
				})

				Router.unlock()
		}
	}
}
