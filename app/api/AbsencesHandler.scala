package api

import actors.SocketHandler
import models._
import models.mysql._
import play.api.libs.json.{JsLookupResult, JsValue, Json}
import utils.SmartTimestamp

trait AbsencesHandler {
	socket: SocketHandler =>

	object Absences {
		/**
		 * $:absences:load
		 */
		def handleLoad(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val user_abs = Slacks.filter(_.user === user.id).sortBy(_.from.desc).take(50).list

			bindEvents {
				case SlackCreate(slack) => slack.user == user.id
				case SlackUpdate(slack) => slack.user == user.id
				case SlackDelete(_) => true
			}

			MessageResults(Json.obj("user" -> user_abs))
		}

		/**
		 * Parse a { day: Int, month: Int } object and return a SmartTimestamp
		 */
		def parseDate(date: JsLookupResult, from_locked: Boolean): SmartTimestamp = {
			val day = (date \ "day").as[Int]
			val month = (date \ "month").as[Int]

			// Optimistically build a date for this year
			val now = SmartTimestamp.today
			val optimistic = SmartTimestamp(now.year, month, day)

			// Check if date is future, if not build a date for next year
			if (optimistic >= now || from_locked) optimistic
			else SmartTimestamp(now.year + 1, month, day)
		}

		/**
		 * Parse and validate a reason for an absence
		 */
		def parseReason(reason: JsLookupResult): Option[String] = {
			reason.asOpt[String] map {
				"^\\s+|\\s+$".r.replaceAllIn(_, "")
			} filter {
				_.length > 0
			}
		}

		/**
		 * Common extractor and checks for absences data
		 */
		type AbsenceDataHandler = (SmartTimestamp, SmartTimestamp, Option[String]) => MessageResponse
		def withAbsenceData(arg: JsValue, from_locked: Boolean)(handler: AbsenceDataHandler): MessageResponse = {
			val from = parseDate(arg \ "from", from_locked)
			val to = parseDate(arg \ "to", false)
			val reason = parseReason(arg \ "reason")

			if (from > to) return MessageFailure("The requested range is invalid")
			if (reason.isEmpty) return MessageFailure("You must provide a reason for this absence")

			handler(from, to, reason)
		}

		/**
		 * $:absences:create
		 */
		def handleCreate(arg: JsValue): MessageResponse = withAbsenceData(arg, false) { (from, to, reason) =>
			val template = Slack(0, user.id, from, to, reason)

			DB.withSession { implicit s =>
				val id = (Slacks returning Slacks.map(_.id)).insert(template)
				Slacks.notifyCreate(template.copy(id = id))
			}

			MessageSuccess
		}

		/**
		 * $:absences:edit
		 */
		def handleEdit(arg: JsValue): MessageResponse = DB.withTransaction { implicit s =>
			val id = (arg \ "id").as[Int]

			val query = Slacks.filter(_.id === id)
			val slack = query.first

			val today = SmartTimestamp.today
			if (today > slack.to) return MessageFailure("You cannot edit a past absence")
			val from_locked = today > slack.from

			withAbsenceData(arg, from_locked) { (from, to, reason) =>
				val updated = slack.copy(
					from = if (today > slack.from) slack.from else from,
					to = to,
					reason = reason)

				if (slack != updated) {
					query.update(updated)
					Slacks.notifyUpdate(updated)
				}

				MessageSuccess
			}
		}

		/**
		 * $:absences:cancel
		 */
		def handleCancel(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val id = (arg \ "id").as[Int]

			val query = Slacks.filter(s => s.id === id && s.user === user.id)
			val slack = query.first
			val today = SmartTimestamp.today

			if (today <= slack.from) {
				query.delete
				Slacks.notifyDelete(id)
			} else if (today <= slack.to) {
				val updated = slack.copy(to = SmartTimestamp(today.year, today.month, today.day - 1))
				query.map(_.to).update(updated.to)
				Slacks.notifyUpdate(updated)
			}

			MessageSuccess
		}
	}
}
