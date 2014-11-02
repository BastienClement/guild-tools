package api

import scala.compat.Platform
import scala.concurrent.Await
import scala.concurrent.duration._
import actors.SocketHandler
import gt.Global.ExecutionContext
import models._
import models.mysql._
import play.api.Play.current
import play.api.libs.json._
import play.api.libs.ws._
import utils.{LazyCell, SmartTimestamp}

object DashboardHelper {
	/**
	 * Cached feed value
	 */
	val Feed = LazyCell[List[Feed]](5.minute) {
		DB.withSession { implicit s =>
			Feeds.sortBy(_.time.desc).take(50).list
		}
	}

	/**
	 * Fetch last public logs from WarcraftLogs
	 */
	val LogsFeed = LazyCell(15.minutes) {
		val time_max = Platform.currentTime / 1000
		val time_min = time_max - (60 * 60 * 24 * 7 * 4)

		val feed = s"http://www.warcraftlogs.com/guilds/calendarfeed/3243/0?start=$time_min&end=$time_max"
		val res = WS.url(feed).get() map { response =>
			Json.parse(response.body).as[List[JsObject]].reverse.take(5)
		}

		try {
			Await.result(res, 10.seconds)
		} catch {
			case _: Throwable => Nil
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
			val ev_from = SmartTimestamp(now.year, now.month, now.day - 1)
			val ev_to = SmartTimestamp(now.year, now.month, now.day + 7)

			val (events, events_filter) = Calendar.loadCalendarAndCreateFilter(ev_from, ev_to)

			socket.bindEvents(events_filter)

			MessageResults(Json.obj(
				"feed" -> DashboardHelper.Feed.value,
				"events" -> Calendar.eventsToJs(events),
				"logs" -> DashboardHelper.LogsFeed.value))
		}
	}
}
