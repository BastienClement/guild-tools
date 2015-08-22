package controllers

import channels.NewsFeed
import play.api.Play._
import play.api.mvc.{Action, Controller}

object Ping extends Controller {
	private val secret = current.configuration.getString("ping.secret") getOrElse ""
	private val sources = current.configuration.getStringSeq("ping.sources") getOrElse Nil

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
		NewsFeed.ping()
	}
}
