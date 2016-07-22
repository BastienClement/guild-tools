package gt.component

import scala.language.postfixOps
import scala.scalajs.js.annotation._
import xuen.rx.Rx
import xuen.{Component, Handler}

/** Main application component */
object GtTest extends Component[GtTest](
	selector = "gt-test",
	templateUrl = "/assets/imports/test.html"
)

@ScalaJSDefined
class GtTest extends Handler {
	val name = attribute[String] := "Unknown"
	val color = attribute[String] := "#64b4ff"
	val isLong = Rx { name.length > 8 }

	var details = property[Boolean] := false

	private var isAttached = false

	println("constructed")


	override def ready() = {
		println("ready")
	}

	override def attached() = {
		println("attached")
		isAttached = true
	}

	override def detached() = println("detached")

	override def attributeChanged(attr: String, old: String, value: String) = if (isAttached) {
		println("attribute changed", attr, old, value)
	}

	def toggle() = {
		details := !details
	}
}
