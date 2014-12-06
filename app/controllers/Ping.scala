package controllers

import api.DashboardHandler
import play.api.mvc.{Action, Controller}

object Ping extends Controller {
	def dashboardFeed = Action {
		DashboardHandler.Feed.refresh()
		Ok("")
	}
}
