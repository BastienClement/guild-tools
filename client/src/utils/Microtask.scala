package utils

import org.scalajs.dom.{MutationObserver, MutationObserverInit, document}
import scala.scalajs.js

object Microtask {
	private[this] val queue = js.Array[js.Function]()
	private[this] val node = document.createElement("span")
	private[this] var scheduled = false
	private[this] var toggle = false

	private[this] val observer = new MutationObserver((_: Any, _: Any) => {
		while (queue.length > 0) {
			queue.shift().call(null)
		}
		scheduled = false
	})

	observer.observe(node, js.Dynamic.literal(attributes = true).asInstanceOf[MutationObserverInit])

	def schedule(fn: => Unit): Unit = {
		queue.push(() => fn)
		if (!scheduled) {
			scheduled = true
			toggle = !toggle
			node.setAttribute("data-trigger", if (toggle) "1" else "0")
		}
	}
}
