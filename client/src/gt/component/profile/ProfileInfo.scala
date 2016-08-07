package gt.component.profile

import gt.component.GtHandler
import gt.component.widget.GtBox
import gt.component.widget.form.GtButton
import org.scalajs.dom.window
import util.jsannotation.js
import xuen.Component

object ProfileInfo extends Component[ProfileInfo](
	selector = "profile-info",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, GtButton)
)

@js class ProfileInfo extends GtHandler {
	val user = property[Int]
	val editable = property[Boolean]

	/** Navigates to the profile management page */
	def edit(): Unit = {
		val base = if (app.dev) "/auth" else "//auth.fromscratch.gg"
		window.location.href = s"$base/user/${ user.! }"
	}
}
