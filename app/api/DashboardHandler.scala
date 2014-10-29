package api

import actors.SocketHandler
import models._
import models.mysql._
import play.api.libs.json._
import utils.{LazyCell, SmartTimestamp}

import scala.concurrent.duration._

object DashboardHelper {
	/**
	 * Cached feed value
	 */
	val Feed = LazyCell[List[Feed]](5.minute) {
		DB.withSession { implicit s =>
			Feeds.sortBy(_.time.desc).take(50).list
		}
	}
}

trait DashboardHandler {
	this: SocketHandler =>

	object Dashboard {
		/**
		 * $:dashboard:load
		 */
		def handleLoad(): MessageResponse = DB.withSession { implicit s =>
			val now = SmartTimestamp.now
			val ev_from = SmartTimestamp.create(now.year, now.month, now.day - 1)
			val ev_to = SmartTimestamp.create(now.year, now.month, now.day + 7)

			val (events, events_filter) = Calendar.loadCalendarAndCreateFilter(ev_from, ev_to)

			socket.bindEvents(events_filter)

			MessageResults(Json.obj(
				"feed" -> DashboardHelper.Feed.get,
				"events" -> Calendar.eventsToJs(events)))
		}
	}
}
