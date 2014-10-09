package api

import java.sql.Timestamp
import java.util.GregorianCalendar

import actors.SocketHandler
import models.{ CalendarEvents, _ }
import models.mysql._
import play.api.libs.json.{ Json, JsNull, JsValue }
import gt.Utils.doIf

trait CalendarHandler {
	self: SocketHandler =>

	private def calendarBounds(month: Int, year: Int): (Timestamp, Timestamp) = {
		val cal = new GregorianCalendar()

		cal.set(year, month - 1, 21)
		val from = new Timestamp(cal.getTime.getTime)

		cal.set(year, month + 1, 15)
		val to = new Timestamp(cal.getTime.getTime)

		(from, to)
	}

	/**
	 * $:calendar:load
	 */
	def handleCalendarLoad(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val month = (arg \ "month").as[Int]
		val year = (arg \ "year").as[Int]

		val (from, to) = calendarBounds(month, year)

		val events = for {
			(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
			if (e.date > from && e.date < to) && (e.etype =!= 3 || a.answer.?.isDefined)
		} yield (e, a.answer.?)

		val events_list = events.list
		var events_id = events_list.map(_._1.id).toSet
		
		socket.eventFilter = {
			case CalendarEventDelete(id) => doIf(events_id.contains(id)) {
				self.synchronized { events_id -= id }
			}
			
			case CalendarAnswerCreate(answer) => (answer.user == user.id)
			case CalendarAnswerUpdate(answer) => (answer.user == user.id)
		}

		MessageResults(events_list map {
			case (e, a) =>
				val answer: JsValue = a.map(Json.toJson(_)).getOrElse(JsNull)
				Json.obj("event" -> e, "answer" -> answer)
		})
	}
}
