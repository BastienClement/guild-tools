package gt.component.profile

import gt.component.GtHandler
import gt.component.widget.{BnetThumb, GtBox}
import gt.service.Roster
import util.jsannotation.js
import xuen.Component

object ProfileUser extends Component[ProfileUser](
	selector = "profile-user",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, BnetThumb)
)

@js class ProfileUser extends GtHandler {
	val user = property[Int]
	val roster = service(Roster)

	val rank = user ~! roster.user ~ (_.group)
	val main = user ~! roster.main
}
