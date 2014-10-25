package api

import actors.SocketHandler
import gt.User
import play.api.libs.json._
import models._
import models.mysql._
import utils.SmartTimestamp

trait DashboardHandler {
	this: SocketHandler =>

	object Dashboard {
		/**
		 * $:dashboard:load
		 */
		def handleLoad(): MessageResponse = DB.withSession { implicit s =>
			val feed = Feeds.sortBy(_.time.desc).take(100).list

			val now = SmartTimestamp.now
			val ev_from = SmartTimestamp.create(now.year, now.month, now.day - 1)
			val ev_to = SmartTimestamp.create(now.year, now.month, now.day + 7)

			val (events, events_filter) = Calendar.loadCalendarAndCreateFilter(ev_from, ev_to)

			println(events);

			socket.bindEvents(events_filter)

			MessageResults(Json.obj(
					"feed" -> feed,
					"events" -> Calendar.eventsToJs(events)))
		}
	}
}
