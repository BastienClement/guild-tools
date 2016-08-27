package gt.components.profile

import gt.components.widget.GtBox
import gt.components.widget.form.GtButton
import gt.components.{GtHandler, Tab, View}
import utils.jsannotation.js
import xuen.Component

object GtProfile extends Component[GtProfile](
	selector = "gt-profile",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(ProfileUser, ProfileInfo, ProfileChars, GtBox, GtButton)
) with View {
	val module = "profile"
	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Profile", "/profile", active = true)
	)
}

@js class GtProfile extends GtHandler {
	val user = attribute[Int] := app.user.id

	/** Whether the profile is editable by the current user */
	val editable = user ~ { id => id == app.user.id || app.user.promoted }
}


