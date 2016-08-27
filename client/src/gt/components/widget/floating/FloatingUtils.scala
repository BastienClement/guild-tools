package gt.components.widget.floating

import facades.dom4.HTMLElement
import org.scalajs.dom.{Event, document}
import scala.scalajs.js

object FloatingUtils {
	def parent(self: AbstractFloating): HTMLElement = {
		if (self.parentNode.nodeType == 11) self.parentNode.asInstanceOf[js.Dynamic].host
		else self.parentNode
	}.asInstanceOf[js.UndefOr[HTMLElement]].orNull

	def lift(self: AbstractFloating, cancel: Event => Unit): Unit = {
		val ph = document.createElement(GtFloatingPlaceholder.selector).asInstanceOf[GtFloatingPlaceholder]
		ph.listener = { e: Event =>
			ph.kill()
			cancel(e)
		}

		self.transition = true

		ph.addEventListener("detached", ph.listener)
		self.parentNode.insertBefore(ph, self)
		document.body.appendChild(self)

		self.placeholder = ph
		self.transition = false
	}

	def unlift(self: AbstractFloating): Unit = {
		self.transition = true

		self.placeholder.kill()
		self.placeholder.parentNode.insertBefore(self, self.placeholder)
		self.placeholder.parentNode.removeChild(self.placeholder)

		self.placeholder = null
		self.transition = false
	}
}
