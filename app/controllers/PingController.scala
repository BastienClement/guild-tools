package controllers

import channels.NewsFeedChannel
import gt.GuildTools
import play.api.mvc.{Action, Controller}

class PingController extends Controller {
	private val secret = GuildTools.conf.getString("ping.secret").getOrElse("")
	private val sources = GuildTools.conf.getStringSeq("ping.sources").getOrElse(Nil)

	private def PingAction(execute: => Unit) = Action { request =>
		val key = request.queryString.getOrElse("key", Nil)
		if (key.nonEmpty && key.head == secret && sources.contains(request.remoteAddress)) {
			execute
			Ok("OK")
		} else {
			Forbidden("NOT OK")
		}
	}

	def dashboardFeed = PingAction {
		NewsFeedChannel.ping()
	}
}
