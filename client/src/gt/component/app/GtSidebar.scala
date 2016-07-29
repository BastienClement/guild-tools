package gt.component.app

import gt.Router
import gt.component.GtHandler
import scala.scalajs.js
import util.annotation.data
import util.jsannotation.js
import xuen.Component
import xuen.rx.Var

object GtSidebar extends Component[GtSidebar](
	selector = "gt-sidebar",
	templateUrl = "/assets/imports/app.html"
)

@js class GtSidebar extends GtHandler {
	/** Sidebar item definition */
	@data case class Icon(icon: String, key: String, link: String, hidden: Boolean = false)

	// Sidebar items
	val icons = js.Array(
		Icon("widgets", "dashboard", "/dashboard"),
		Icon("account_circle", "profile", "/profile"),
		Icon("mail", "messages", "/messages"),
		Icon("today", "calendar", "/calendar"),
		Icon("supervisor_account", "roster", "/roster"),
		Icon("assignment_ind", "apply", if (app.user.roster) "/apply" else "/apply-guest"),
		Icon("airplay", "streams", "/streams")
	).filter(!_.hidden)

	// Tracks the module ID of the current view
	val module = Var(null: String)
	Router.onUpdate ~> { case (_, _, view) => module := view.module }

	/** Navigates to the requested path */
	def navigate(path: String): Unit = Router.goto(path)
}
