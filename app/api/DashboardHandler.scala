package api

import actors.SocketHandler
import gt.User
import play.api.libs.json._
import models._
import models.mysql._

trait DashboardHandler {
	this: SocketHandler =>

	object Dashboard {
		/**
		 * $:dashboard:load
		 */
		def handleLoad(): MessageResponse = DB.withSession { implicit s =>
			val feed = Feeds.sortBy(_.time.desc).take(100).list
			MessageResults(Json.obj("feed" -> feed))
		}
	}
}
