package gt.components.profile

import gt.components.GtHandler
import gt.components.widget.{BnetThumb, GtBox}
import gt.services.RosterService
import utils.jsannotation.js
import xuen.Component

object ProfileUser extends Component[ProfileUser](
	selector = "profile-user",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, BnetThumb)
)

@js class ProfileUser extends GtHandler {
	val user = property[Int]
	val roster = service(RosterService)

	val rank = user ~! roster.user ~ (_.group)
	val main = user ~! roster.main
}
