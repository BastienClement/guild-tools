package gt.component

import java.util.Random
import scala.language.postfixOps
import scala.scalajs.js
import util.jsannotation.js
import xuen.rx.{Rx, Var}
import xuen.{Component, Handler}

/** Main application component */
object GtTest extends Component[GtTest](
	selector = "gt-test",
	templateUrl = "/assets/imports/test.html"
)

@js class GtTest extends Handler {
	val name = attribute[String] := "Unknown"
	val color = attribute[String] := "#64b4ff"
	val isLong = Rx { name.length > 8 }

	var details = property[Boolean] := false

	val itemsMap: Var[Map[String, Int]] = Map("a" -> 1, "b" -> 2, "c" -> 3)
	val itemsArr: Var[js.Array[String]] = scalajs.js.Array("a", "b", "c")
	var items: Var[Seq[String]] = Seq("a", "b", "c")

	var counter: Var[Int] = 0

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
		itemsArr ~= (_ :+ Integer.toHexString(new Random().nextInt))
	}
}
