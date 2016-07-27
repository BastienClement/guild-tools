package gt.component.app

import gt.App
import scala.scalajs.js
import util.annotation.data
import util.jsannotation.js
import xuen.{Component, Handler}

object GtSidebar extends Component[GtSidebar](
	selector = "gt-sidebar",
	templateUrl = "/assets/imports/app.html"
)

@js class GtSidebar extends Handler {
	@data case class Icon(icon: String, key: String, link: String)

	val icons = js.Array(
		Icon("widgets", "dashboard", "/dashboard"),
		Icon("account_circle", "profile", "/profile"),
		Icon("mail", "messages", "/messages"),
		Icon("today", "calendar", "/calendar"),
		Icon("supervisor_account", "roster", "/roster"),
		Icon("assignment_ind", "apply", if (true || App.user.roster) "/apply" else "/apply-guest"),
		Icon("airplay", "streams", "/streams")
	)
}
