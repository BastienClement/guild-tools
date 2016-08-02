package gt.component

import model.User
import scala.language.existentials
import xuen.Component

trait View {
	self: Component[_] =>

	val component: Component[_] = this

	val selector: String
	val module: String
	val sticky: Boolean = false

	type TabGenerator = (String, String, User) => Seq[Tab]
	val tabs: TabGenerator
}

object View {
	trait Sticky extends View {
		self: Component[_] =>
		override val sticky: Boolean = true
	}
}
