package gt.components.profile

import gt.components.GtHandler
import gt.components.widget.GtBox
import gt.components.widget.form.GtButton
import gt.services.RosterService
import utils.jsannotation.js
import xuen.Component

object ProfileChars extends Component[ProfileChars](
	selector = "profile-chars",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, GtButton, ProfileCharsCard, ProfileAddChar)
)

@js class ProfileChars extends GtHandler {
	val user = property[Int]
	val editable = property[Boolean]

	val roster = service(RosterService)

	val me = user ~ (_ == app.user.id)
	val chars = user ~! roster.toons
	val empty = chars ~ (_.isEmpty)

	def print(any: Any) = println(any)
}
