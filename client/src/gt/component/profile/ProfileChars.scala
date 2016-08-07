package gt.component.profile

import gt.component.GtHandler
import gt.component.widget.GtBox
import gt.component.widget.form.GtButton
import gt.service.Roster
import util.jsannotation.js
import xuen.Component

object ProfileChars extends Component[ProfileChars](
	selector = "profile-chars",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, GtButton, ProfileCharsCard, ProfileAddChar)
)

@js class ProfileChars extends GtHandler {
	val user = property[Int]
	val editable = property[Boolean]

	val roster = service(Roster)

	val me = user ~ (_ == app.user.id)
	val chars = user ~! roster.toons
	val empty = chars ~ (_.isEmpty)

	def print(any: Any) = println(any)
}
