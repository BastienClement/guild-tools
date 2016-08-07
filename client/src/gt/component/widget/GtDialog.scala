package gt.component.widget

import gt.component.GtHandler
import org.scalajs.dom._
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import util.jsannotation.js
import xuen.Component

object GtDialog extends Component[GtDialog](
	selector = "gt-dialog",
	templateUrl = "/assets/imports/dialog.html"
) {
	/** Pending modal dialogs */
	private[GtDialog] val queue = mutable.Queue[GtDialog]()

	/** A modal window is currently visible */
	private[GtDialog] var visible = null: GtDialog
}

@js class GtDialog extends GtHandler {
	/** A sticky dialog does not autoclose if the user click outside of it */
	val sticky = attribute[Boolean]

	/**
	  * If defined, the dialog will be moved to the <body> element
	  * while visible. This will break CSS scoping but allow it to
	  * escape absolute positioning.
	  */
	val escape = attribute[Boolean]

	/** The dialog placeholder while visible */
	private var placeholder = null: Node

	/** True if the dialog is currently shown */
	def shown: Boolean = GtDialog.visible == this

	/** Show the dialog if no other modal is opened */
	def show(force: Boolean = false): Unit = {
		if (GtDialog.visible != null) {
			if (force) {
				GtDialog.visible.hide().foreach { _ => show(force) }
			} else {
				if (!GtDialog.queue.contains(this)) {
					GtDialog.queue.enqueue(this)
				}
			}
			return
		}

		if (escape) {
			placeholder = document.createComment(" dialog placeholder ")
			parentNode.insertBefore(placeholder, this)
			document.body.appendChild(this)
		}

		GtDialog.visible = this
		classList.add("visible")
		child.slider.classList.add("slide-in")

		fire("show")
	}

	def hide(): Future[Unit] = if (shown) {
		val promise = Promise[Unit]()
		lazy val listener: js.Function1[Event, Any] = animationEndListener(listener, promise)
		classList.add("fade-out")
		addEventListener("animationend", listener)
		promise.future
	} else {
		Future.successful(())
	}

	private def animationEndListener(self: => js.Function1[Event, Any], promise: Promise[Unit]): Event => Any = { e =>
		removeEventListener("animationend", self)
		close()
		promise.success(())
	}

	private def close(): Unit = {
		child.slider.classList.remove("slide-in")
		classList.remove("visible")
		classList.remove("fade-out")

		if (GtDialog.visible == this) {
			GtDialog.visible = null
		}

		if (escape) {
			placeholder.parentNode.insertBefore(this, placeholder)
			placeholder.parentNode.removeChild(placeholder)
			placeholder = null
		}

		fire("hide")

		if (GtDialog.queue.nonEmpty) {
			GtDialog.queue.dequeue().show(true)
		}
	}

	override def detached(): Unit = if (GtDialog.visible == this) {
		this.close()
	}

	listen("click") { e: MouseEvent =>
		if (!sticky) this.hide()
	}
}
