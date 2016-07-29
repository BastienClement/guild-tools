package gt.component

import model.User
import scala.language.existentials
import xuen.Component

trait View {
	self: Component[_] =>

	val selector: String
	val module: String

	type TabGenerator = (String, String, User) => Seq[Tab]
	val tabs: TabGenerator
}
