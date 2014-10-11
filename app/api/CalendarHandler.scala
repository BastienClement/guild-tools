package api

import java.sql.Timestamp
import java.util.GregorianCalendar
import actors.SocketHandler
import models.{ CalendarEvents, _ }
import models.mysql._
import play.api.libs.json.{ Json, JsNull, JsValue }
import utils.SmartTimestamp.Implicits._
import scala.collection.mutable
import utils.SmartTimestamp
import java.text.SimpleDateFormat
import java.text.ParseException

trait CalendarHandler {
	self: SocketHandler =>

	/**
	 * $:calendar:load
	 */
	def handleCalendarLoad(arg: JsValue): MessageResponse = {
		val month = (arg \ "month").as[Int]
		val year = (arg \ "year").as[Int]

		val from = SmartTimestamp.createSQL(year, month - 1, 21)
		val to = SmartTimestamp.createSQL(year, month + 1, 15)

		val events = for {
			(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
			if (e.date > from && e.date < to) && (e.visibility =!= CalendarVisibility.Private || a.answer.?.isDefined)
		} yield (e, a.answer.?)

		val events_list = DB.withSession { events.list(_) }

		var watched_events = events_list.map(_._1.id).toSet
		var potential_events = mutable.Map[Int, Event]()

		socket.eventFilter = {
			// Event created
			case ev @ CalendarEventCreate(event) => {
				if (watched_events.contains(event.id)) {
					// Delayed broadcast for private events
					true
				} else if (event.date.between(from, to)) {
					// Visible event
					if (event.visibility == CalendarVisibility.Private && event.owner != user.id) {
						// Delay transmission of this event until invitation event
						potential_events += (event.id -> ev)
						false
					} else {
						// Visible event, let's go!
						watched_events += event.id
						true
					}
				} else {
					// Outside view
					false
				}
			}

			// Event updated
			case CalendarEventUpdate(event) => {
				if (potential_events.contains(event.id)) {
					potential_events(event.id) = CalendarEventCreate(event)
					false
				} else {
					watched_events.contains(event.id)
				}
			}

			// Event deletes
			case CalendarEventDelete(id) => {
				potential_events -= id
				utils.doIf(watched_events.contains(id)) { watched_events -= id }
			}

			// Answer created
			case ev @ CalendarAnswerCreate(answer) => {
				val eid = answer.event
				if (answer.user != user.id) {
					false
				} else if (potential_events.contains(eid)) {
					socket ! potential_events(eid)
					socket ! ev
					watched_events += eid
					potential_events -= eid
					false
				} else {
					watched_events.contains(eid)
				}
			}

			// Answer updated
			case CalendarAnswerUpdate(answer) => {
				answer.user == user.id && watched_events.contains(answer.event)
			}

			case CalendarAnswerDelete(answer_user, event) => {
				answer_user == user.id && watched_events.contains(event)
			}
		}

		MessageResults(events_list map {
			case (e, a) =>
				Json.obj("id" -> e.id, "event" -> e, "answer" -> (a.map(Json.toJson(_)).getOrElse(JsNull): JsValue))
		})
	}

	/**
	 * $:calendar:create
	 */
	def handleCalendarCreate(arg: JsValue): MessageResponse = {
		val title = (arg \ "title").as[String]
		val desc = (arg \ "desc").as[String]
		val visibility = (arg \ "type").as[Int]
		val hour = (arg \ "hour").as[Int]
		val minutes = (arg \ "min").as[Int]
		val dates_raw = (arg \ "dates").as[List[String]]

		def inRange(a: Int, b: Int, c: Int) = (a >= b && a <= c)

		if (!CalendarVisibility.isValid(visibility) || !inRange(hour, 0, 23) || !inRange(minutes, 0, 59)) {
			return MessageFailure("INVALID_EVENT_DATA")
		}

		if (dates_raw.length > 1) {
			if (!user.officer) {
				return MessageFailure("MULTI_NOT_ALLOWED")
			}

			if (visibility == CalendarVisibility.Announce) {
				return MessageAlert("Creating announces on multiple days is not allowed.")
			}
		}

		if (visibility != CalendarVisibility.Private && !user.officer) {
			return MessageAlert("Members can only create restricted events.")
		}

		val format = new SimpleDateFormat("yyyy-MM-dd")
		val now = SmartTimestamp.now

		def isValidDate(ts: SmartTimestamp): Boolean = {
			val delta = (ts - now).time / 1000
			if (delta < -86400 || delta > 5270400) false else true
		}

		val dates = dates_raw map { date =>
			try {
				Some(SmartTimestamp.parse(date, format))
			} catch {
				case e: ParseException => None
			}
		} collect {
			case Some(ts) if isValidDate(ts) => ts
		}

		if (dates.length < 1) {
			return MessageFailure("INVALID_DATES")
		}

		DB.withSession { implicit s =>
			dates foreach { date =>
				val template = CalendarEvent(
					id = 0,
					title = title,
					desc = desc,
					owner = user.id,
					date = date,
					time = hour * 100 + minutes,
					`type` = visibility,
					state = 0)

				val id: Int = (CalendarEvents returning CalendarEvents.map(_.id)) += template
				CalendarEvents.notifyCreate(template.copy(id = id))

				if (visibility != CalendarVisibility.Announce) {
					val answer = CalendarAnswer(user.id, id, now, 1, None, None)
					CalendarAnswers += answer
					CalendarAnswers.notifyCreate(answer)
				}
			}
		}

		MessageSuccess
	}

	/**
	 * $:calendar:answer
	 */
	def handleCalendarAnswer(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val event_id = (arg \ "id").as[Int]
		val answer = (arg \ "answer").as[Int]
		val note_raw = (arg \ "note").asOpt[String]
		val char = (arg \ "char").asOpt[Int]

		val row = for {
			(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
			if (e.id === event_id) && (e.visibility =!= CalendarVisibility.Private || a.answer.?.isDefined)
		} yield (e, a.answer.?, a.note, a.char)

		row.firstOption map {
			case (event, old_answer, old_note, old_char) =>
				if (event.visibility == CalendarVisibility.Public && old_answer.isEmpty) {
					return MessageFailure("UNINVITED")
				}

				if (event.state != CalendarEventState.Open) {
					return MessageFailure("CLOSED_EVENT")
				}

				val note = (if (note_raw.isDefined) note_raw else old_note) filter (!_.matches("^\\s*$"))

				val template = CalendarAnswer(
					user = user.id,
					event = event_id,
					date = SmartTimestamp.now,
					answer = answer,
					note = note,
					char = if (char.isDefined) char else old_char)

				CalendarAnswers.insertOrUpdate(template)
				CalendarAnswers.notifyUpdate(template)

				MessageSuccess
		} getOrElse {
			MessageFailure("EVENT_NOT_FOUND")
		}
	}

	/**
	 * $:calendar:delete
	 */
	def handleCalendarDelete(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val event_id = (arg \ "id").as[Int]

		var query = CalendarEvents.filter(_.id === event_id)
		if (!user.officer) {
			query = query.filter(_.owner === user.id)
		}

		if (query.delete > 0) {
			CalendarEvents.notifyDelete(event_id)
			MessageSuccess
		} else {
			MessageFailure("EVENT_NOT_FOUND")
		}
	}

	/**
	 * $:calendar:event
	 */
	def handleCalendarEvent(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
		val event_id = (arg \ "id").as[Int]

		val event = CalendarEvents.filter(_.id === event_id).first

		MessageSuccess
	}
}
