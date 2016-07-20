package util

import org.scalajs.dom
import scala.scalajs.js

object Global {
	final val literal = js.Dynamic.literal
	final val dynamic = scalajs.js.Dynamic.global
	final val console = dom.window.console
	final val document = dom.document

	@inline def setTimeout(duration: Double)(fn: => Unit) = dom.window.setTimeout(() => fn, duration)
}
