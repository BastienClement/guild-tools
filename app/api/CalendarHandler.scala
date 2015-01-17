package api

import java.sql.Timestamp
import java.text.{ParseException, SimpleDateFormat}
import scala.util.Try
import actors.Actors.CalendarService
import actors.CalendarService.CalendarLock
import actors.{AuthService, SocketHandler}
import gt.Global.ExecutionContext
import models.mysql._
import models.{CalendarEvents, _}
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json.{JsNull, JsValue, Json}
import utils.SmartTimestamp.fromTimestamp
import utils.{EventFilter, SmartTimestamp}
import scala.concurrent.duration._

/**
 * Shared calendar-related values
 */
object CalendarHandler {
	val guildies_groups = Set[Int](8, 9, 11)

	val missingAnswers = Compiled { (event_id: Column[Int]) =>
		for {
			u <- Users if u.group inSet guildies_groups
			if !CalendarAnswers.filter(a => u.id === a.user && a.event === event_id).exists
		} yield u.id
	}

	val answersQuery = Compiled { (event_id: Column[Int]) =>
		for {
			a <- CalendarAnswers if a.event === event_id
			u <- Users if u.id === a.user
		} yield (u, a)
	}
}

/**
 * Implements calendar-related API
 */
trait CalendarHandler {
	socket: SocketHandler =>

	object Calendar {
		type EventsAndAnswers = List[(CalendarEvent, Option[Int])]

		/**
		 * Event context
		 */
		var event_current: CalendarEvent = null
		var event_editable = false
		var event_tabs = Map[Int, CalendarTab]()
		var edit_lock: Option[CalendarLock] = None

		/**
		 * Reset the event context
		 */
		def resetEventContext() = {
			event_current = null
			event_editable = false
			event_tabs = Map()
			edit_lock = edit_lock flatMap { lock =>
				lock.release()
				None
			}
		}

		/**
		 * Ensure the given tabs are editable in the current context
		 */
		def ensureTabEditable(tabs: Int*)(body: => MessageResponse): MessageResponse = {
			if (event_editable && tabs.forall { event_tabs.contains(_) })
				body
			else
				MessageFailure("FORBIDDEN")
		}

		/**
		 * Load user events between two dates
		 */
		def loadCalendarEvents(from: Timestamp, to: Timestamp): EventsAndAnswers = {
			DB.withSession { implicit s =>
				val events = for {
					(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
					if (e.date >= from && e.date <= to) && (e.visibility =!= CalendarVisibility.Restricted || a.answer.?.isDefined)
				} yield (e, a.answer.?)
				events.list
			}
		}

		/**
		 * Create an event filter for current calendar page
		 */
		def createCalendarFilter(events: Set[Int], from: Timestamp, to: Timestamp): EventFilter.FilterFunction = {
			var watched_events = events

			def slackFilter(slack: Slack, wrap: (Slack) => CtxEvent): Boolean = {
				if (slack.from >= from || slack.to <= to) {
					socket !< wrap(slack)
				} else {
					false
				}
			}

			return {
				// Event created
				case ev@CalendarEventCreate(event) => {
					if (watched_events.contains(event.id)) {
						// Delayed broadcast for private events
						true
					} else if (event.date.between(from, to)) {
						// Visible event
						if (event.isRestricted && event.owner != user.id) {
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
				case CalendarEventUpdate(event) => watched_events.contains(event.id)

				// Event deletes
				case CalendarEventDelete(eid) => utils.doIf(watched_events.contains(eid)) {
					watched_events -= eid
				}

				// Answer created
				case ev@CalendarAnswerCreate(answer) => {
					val eid = answer.event
					if (answer.user != user.id) {
						false
					} else if (!watched_events.contains(eid)) {
						Try {
							self ! CalendarEventCreate(answer.fullEvent)
							self ! ev
							watched_events += eid
						}
						false
					} else {
						true
					}
				}

				// Answer updated
				case CalendarAnswerUpdate(answer) => {
					answer.user == user.id && watched_events.contains(answer.event)
				}

				case CalendarAnswerDelete(answer_user, event) => {
					utils.doIf(answer_user == user.id && watched_events.contains(event)) {
						watched_events -= event
					}
				}

				case SlackCreate(slack) => slackFilter(slack, SlackCreate(_))
				case SlackUpdate(slack) => slackFilter(slack, SlackUpdate(_))
				case SlackDelete(slack) => true
			}
		}

		/**
		 * Helper for load + create event filter
		 */
		def loadCalendarAndCreateFilter(from: Timestamp, to: Timestamp): (EventsAndAnswers, EventFilter.FilterFunction) = {
			val events = loadCalendarEvents(from, to)
			val filter = createCalendarFilter(events.map(_._1.id).toSet, from, to)
			(events, filter)
		}

		/**
		 * Convert EventsAndAnswers to JsArray
		 */
		def eventsToJs(ea: EventsAndAnswers): JsValueWrapper = {
			ea map {
				case (e, a) =>
					Json.obj("id" -> e.id, "event" -> e, "answer" -> (a.map(Json.toJson(_)).getOrElse(JsNull): JsValue))
			}
		}

		/**
		 * $:calendar:load
		 */
		def handleLoad(arg: JsValue): MessageResponse = {
			val month = (arg \ "month").as[Int]
			val year = (arg \ "year").as[Int]

			val from = SmartTimestamp(year, month - 1, 21)
			val to = SmartTimestamp(year, month + 1, 15)

			// Calendar events
			val (events, filter) = loadCalendarAndCreateFilter(from, to)

			// Matching absences
			val slacks = DB.withSession { implicit s =>
				val slacks_query = for {
					u <- Users if u.group inSet AuthService.allowedGroups
					s <- Slacks if (s.from >= from.toSQL || s.to <= to.toSQL) && s.user === u.id
				} yield s

				slacks_query.list.map(_.conceal)
			}

			// Listen to both calendar events and absences events
			socket.bindEvents(filter)

			Json.obj("events" -> eventsToJs(events), "absences" -> slacks)
		}

		/**
		 * $:calendar:create
		 */
		def handleCreate(arg: JsValue): MessageResponse = {
			val title = (arg \ "title").as[String]
			val desc = (arg \ "desc").as[String]
			val visibility = (arg \ "type").as[Int]
			val hour = (arg \ "hour").as[Int]
			val minutes = (arg \ "min").as[Int]
			val dates_raw = (arg \ "dates").as[List[String]]

			def inRange(a: Int, b: Int, c: Int) = a >= b && a <= c

			if (!CalendarVisibility.isValid(visibility) || !inRange(hour, 0, 23) || !inRange(minutes, 0, 59)) {
				return MessageFailure("INVALID_EVENT_DATA")
			}

			if (dates_raw.length > 1) {
				if (!user.promoted) return MessageFailure("MULTI_NOT_ALLOWED")
				if (visibility == CalendarVisibility.Announce) return MessageFailure("Creating announces on multiple days is not allowed.")
			}

			if (visibility != CalendarVisibility.Restricted && visibility != CalendarVisibility.Optional && !user.promoted)
				return MessageFailure("Members can only create optional or restricted events.")

			val format = new SimpleDateFormat("yyyy-MM-dd")
			val now = SmartTimestamp.now

			def isValidDate(ts: SmartTimestamp): Boolean = {
				val delta = (ts - now).toSeconds
				!(delta < -86400 || delta > 5270400)
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

			if (dates.length < 1) return MessageFailure("INVALID_DATES")

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
						// Create the default tab
						CalendarTabs += CalendarTab(0, id, "Default", None, 0, false, true)

						// Invite the owner into his event
						val answer = CalendarAnswer(user.id, id, now, 1, None, None, true)
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
		def handleAnswer(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val event_id = (arg \ "event").as[Int]
			val answer = (arg \ "answer").as[Int]
			val note = (arg \ "note").asOpt[String]
			val char = (arg \ "char").asOpt[Int]

			val event = CalendarEvents.filter(_.id === event_id).first
			val old_answer = CalendarAnswers.filter(a => a.user === user.id && a.event === event_id).firstOption

			if (event.isRestricted && old_answer.isEmpty)
				return MessageFailure("UNINVITED")

			if (event.state != CalendarEventState.Open)
				return MessageFailure("CLOSED_EVENT")

			val template = CalendarAnswer(
				user = user.id,
				event = event_id,
				date = SmartTimestamp.now,
				answer = answer,
				note = note orElse old_answer.flatMap(_.note),
				char = char orElse old_answer.flatMap(_.char),
				promote = false)

			CalendarAnswers.insertOrUpdate(template)
			CalendarAnswers.notifyUpdate(template)

			MessageSuccess
		}

		/**
		 * $:calendar:delete
		 */
		def handleDelete(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val event_id = (arg \ "id").as[Int]

			var query = CalendarEvents.filter(_.id === event_id)
			if (!user.promoted) {
				query = query.filter(_.owner === user.id)
			}

			if (query.map(_.garbage).update(true) > 0) {
				CalendarEvents.notifyDelete(event_id)
				MessageSuccess
			} else {
				MessageFailure("EVENT_NOT_FOUND")
			}
		}

		/**
		 * $:calendar:event
		 */
		def handleEvent(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val id = (arg \ "id").as[Int]
			val event = CalendarEvents.filter(_.id === id).first

			if (event.isAnnounce) {
				return MessageFailure("OPENING_ANNOUNCE")
			}

			val answers = {
				// Fetch registerd users
				val event_answers = CalendarHandler.answersQuery(id).list.map({
					case (u: User, a: CalendarAnswer) => (u.id.toString, Some(a))
				}).toMap

				// Add non-register if this is a guild of optional event
				if (event.visibility == CalendarVisibility.Guild || event.visibility == CalendarVisibility.Optional) {
					event_answers ++ CalendarHandler.missingAnswers(id).list.map(u => (u.toString, None)).toMap
				} else {
					event_answers
				}
			}

			// Extract own answer
			val my_answer = answers.get(user.id.toString)

			// Check invitation in private event
			if (event.isRestricted && my_answer.isEmpty) return MessageFailure("NOT_INVITED")

			// Record successful event access
			event_current = event
			event_editable = (event.owner == user.id) || user.promoted

			// Check for promote
			for {
				opt_answer <- my_answer
				answer <- opt_answer if answer.promote
			} event_editable = true

			// Expand or conceal event
			val visible = {
				if (event_editable) {
					event.expand
				} else {
					event.partial
				}
			}

			// Extract tabs set
			event_tabs = visible.tabs.map(tab => (tab.id -> tab)).toMap

			def tabContentIsVisible(tab_id: Int): Boolean = {
				event_tabs.get(tab_id) map { tab =>
					event_editable || !tab.locked
				} getOrElse {
					false
				}
			}

			// Load absents for this event
			val slacks_query = for {
				u <- Users if u.group inSet AuthService.allowedGroups
				s <- Slacks if s.from <= event.date && s.to >= event.date && s.user === u.id
			} yield s

			val slacks = if (user.promoted) slacks_query.list else slacks_query.list.map(_.conceal)

			def slackFilter(slack: Slack, wrap: (Slack) => CtxEvent): Boolean = {
				if (slack.from <= event.date && slack.to >= event.date) {
					if (user.promoted) {
						true
					} else {
						socket !< wrap(slack.conceal)
					}
				} else {
					false
				}
			}

			// Handle changes in answers
			def handleAnswerChanges(answer: CalendarAnswer): Boolean = utils.doIf(answer.event == id) {
				if (answer.user == user.id) {
					val editable = answer.promote || (event.owner == user.id) || user.promoted
					if (editable != event_editable) {
						event_editable = editable
						event_current = event_current.copy()
						val event_view = if (editable) event_current.expand else event_current.partial
						socket !~ CalendarEventUpdateFull(event_view)
					}
				}
			}

			// Event page bindings
			bindEvents {
				case CalendarAnswerCreate(answer) => handleAnswerChanges(answer)
				case CalendarAnswerUpdate(answer) => handleAnswerChanges(answer)

				case CalendarAnswerDelete(uid, eid) => utils.doIf(eid == id) {
					if (uid == user.id) {
						// Just got kicked from the event
						resetEventContext()
					}
				}

				case CalendarEventUpdate(ev) => utils.doIf(ev.id == id) {
					event_current = ev
				}

				case CalendarEventDelete(eid) => eid == id

				case CalendarTabCreate(tab) => utils.doIf(tab.event == id) {
					event_tabs += (tab.id -> tab)
				}

				case CalendarTabWipe(tab_id) => tabContentIsVisible(tab_id)

				case CalendarTabUpdate(tab) => {
					if (!event_tabs.contains(tab.id)) {
						false
					} else {
						val old = event_tabs(tab.id)
						event_tabs = event_tabs.updated(tab.id, tab)

						if (event_editable || old.locked == tab.locked) {
							// Tab is always visible or visibility hasn't changed
							true
						} else if (tab.locked) {
							// Tab was visible, is now locked
							val concealed = tab.copy(locked = true, note = None)
							concealed != old && socket !< CalendarTabUpdate(concealed)
						} else {
							// Tab was locked, is now visible
							socket !< CalendarEventUpdateFull(tab.expandEvent.partial)
						}
					}
				}

				case CalendarTabDelete(id) => utils.doIf(event_tabs.contains(id)) {
					event_tabs -= id
				}

				case CalendarSlotUpdate(slot) => tabContentIsVisible(slot.tab)
				case CalendarSlotDelete(tab, _) => tabContentIsVisible(tab)

				case CalendarLockAcquire(tab, _) => tabContentIsVisible(tab) && event_editable
				case CalendarLockRelease(tab) => tabContentIsVisible(tab) && event_editable

				case SlackCreate(slack) => slackFilter(slack, SlackCreate(_))
				case SlackUpdate(slack) => slackFilter(slack, SlackUpdate(_))
				case SlackDelete(_) => true
			} onUnbind {
				resetEventContext()
			}

			Json.obj(
				"event" -> event,
				"answers" -> answers,
				"answer" -> my_answer,
				"tabs" -> visible.tabs,
				"slots" -> visible.slots,
				"editable" -> event_editable,
				"absences" -> slacks)
		}

		/**
		* $:calendar:event:invite
		*/
		def handleEventInvite(arg: JsValue): MessageResponse = {
			val users = (arg \ "users").as[List[Int]]
			if (!event_editable) return MessageFailure("FORBIDDEN")

			DB.withSession { implicit s =>
				for (user <- users) {
					val answer = CalendarAnswer(user, event_current.id, SmartTimestamp.now, 0, None, None, false)
					Try {
						CalendarAnswers.insert(answer)
						CalendarAnswers.notifyCreate(answer)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:event:state
		 */
		def handleEventState(arg: JsValue): MessageResponse = {
			val state = (arg \ "state").as[Int]

			if (!CalendarEventState.isValid(state)) return MessageFailure("BAD_STATE")
			if (!event_editable && !user.promoted) return MessageFailure("FORBIDDEN")

			val event_id = (arg \ "event").asOpt[Int] getOrElse event_current.id

			DB.withTransaction { implicit s =>
				val query = CalendarEvents.filter(_.id === event_id)
				query.map(_.state).update(state)
				CalendarEvents.notifyUpdate(query.first.copy(state = state))
			}

			MessageSuccess
		}

		/**
		 * $:calendar:event:editdesc
		 */
		def handleEventEditDesc(arg: JsValue): MessageResponse = {
			val title = (arg \ "title").as[String]
			val desc = (arg \ "desc").as[String]
			if (!event_editable) return MessageFailure("FORBIDDEN")

			DB.withTransaction { implicit s =>
				val query = CalendarEvents.filter(_.id === event_current.id)
				query.map(e => (e.title, e.desc)).update((title, desc))
				CalendarEvents.notifyUpdate(query.first.copy(title = title, desc = desc))
			}

			MessageSuccess
		}

		/**
		* $:calendar:event:promote
		* $:calendar:event:demote
		*/
		def handleEventPromote(promote: Boolean)(arg: JsValue): MessageResponse = {
			val user = (arg \ "user").as[Int]
			if (!event_editable) return MessageFailure("FORBIDDEN")

			DB.withTransaction { implicit s =>
				val row = CalendarAnswers.filter(a => a.event === event_current.id && a.user === user)
				if (row.map(_.promote).update(promote) < 1 && promote) {
					val template = CalendarAnswer(user, event_current.id, SmartTimestamp.now, 0, None, None, true)
					if (CalendarAnswers.insert(template) > 0) {
						CalendarAnswers.notifyCreate(template)
					}
				} else {
					CalendarAnswers.notifyUpdate(row.first)
				}
			}

			MessageSuccess
		}

		/**
		* $:calendar:event:kick
		*/
		def handleEventKick(arg: JsValue): MessageResponse = {
			val user = (arg \ "user").as[Int]

			if (!event_editable || user == event_current.owner || !event_current.isRestricted)
				return MessageFailure("FORBIDDEN")

			DB.withSession { implicit s =>
				if (CalendarAnswers.filter(a => a.event === event_current.id && a.user === user).delete > 0) {
					CalendarAnswers.notifyDelete(user, event_current.id)
				}
			}

			MessageSuccess
		}

		/**
		 * $:calendar:comp:set
		 * $:calendar:comp:reset
		 */
		def handleCompSet(reset: Boolean)(arg: JsValue): MessageResponse = {
			val tab = (arg \ "tab").as[Int]
			val slot = (arg \ "slot").as[Int]

			if (slot < 0 || slot > 30) return MessageFailure("BAD_SLOT")

			ensureTabEditable(tab) {
				if (reset) {
					DB.withSession { implicit s =>
						CalendarSlots.filter(s => s.tab === tab && s.slot === slot).delete
						CalendarSlots.notifyDelete(tab, slot)
					}
				} else {
					val owner = (arg \ "char" \ "owner").as[Int]
					val name = (arg \ "char" \ "name").as[String]
					val clazz = (arg \ "char" \ "class").as[Int]
					val role = (arg \ "char" \ "role").as[String]

					val template = CalendarSlot(tab, slot, owner, name, clazz, role)

					DB.withSession { implicit s =>
						CalendarSlots.filter(s => s.tab === tab && s.owner === owner).delete
						CalendarSlots.insertOrUpdate(template)
						CalendarSlots.notifyUpdate(template)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:create
		 */
		def handleTabCreate(arg: JsValue): MessageResponse = {
			val title = (arg \ "title").as[String]

			if (!event_editable) return MessageFailure("FORBIDDEN")

			CalendarService.createTab(event_current.id, title) map { tab =>
				CalendarTabs.notifyCreate(tab)
				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:delete
		 */
		def handleTabDelete(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			ensureTabEditable(tab_id) {
				DB.withSession { implicit s =>
					if (CalendarTabs.filter(t => t.id === tab_id && !t.undeletable).delete > 0) {
						CalendarTabs.notifyDelete(tab_id)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:swap
		 */
		def handleTabSwap(arg: JsValue): MessageResponse = {
			val tab1_id = (arg \ "a").as[Int]
			val tab2_id = (arg \ "b").as[Int]
			ensureTabEditable(tab1_id, tab2_id) {
				DB.withTransaction { implicit s =>
					val tab1 = CalendarTabs.filter(_.id === tab1_id).first
					val tab2 = CalendarTabs.filter(_.id === tab2_id).first

					val tab1_new = tab1.copy(order = tab2.order)
					val tab2_new = tab2.copy(order = tab1.order)

					CalendarTabs.filter(_.id === tab1_id).map(_.order).update(tab2.order)
					CalendarTabs.filter(_.id === tab2_id).map(_.order).update(tab1.order)

					CalendarTabs.notifyUpdate(tab1_new)
					CalendarTabs.notifyUpdate(tab2_new)
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:rename
		 */
		def handleTabRename(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			val title = (arg \ "title").as[String]
			ensureTabEditable(tab_id) {
				DB.withSession { implicit s =>
					val tab_query = CalendarTabs.filter(_.id === tab_id)
					if (tab_query.map(_.title).update(title) > 0) {
						CalendarTabs.notifyUpdate(tab_query.first)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:wipe
		 */
		def handleTabWipe(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			ensureTabEditable(tab_id) {
				DB.withSession { implicit s =>
					if (CalendarSlots.filter(_.tab === tab_id).delete > 0) {
						CalendarTabs.notifyWipe(tab_id)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:edit
		 */
		def handleTabEdit(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			val note = (arg \ "note").asOpt[String]
			ensureTabEditable(tab_id) {
				DB.withSession { implicit s =>
					val tab_query = CalendarTabs.filter(_.id === tab_id)
					if (tab_query.map(_.note).update(note) > 0) {
						CalendarTabs.notifyUpdate(tab_query.first)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:tab:lock
		 * $:calendar:tab:unlock
		 */
		def handleTabLock(lock: Boolean)(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			ensureTabEditable(tab_id) {
				DB.withSession { implicit s =>
					val tab_query = CalendarTabs.filter(_.id === tab_id)
					if (tab_query.map(_.locked).update(lock) > 0) {
						CalendarTabs.notifyUpdate(tab_query.first)
					}
				}

				MessageSuccess
			}
		}

		/**
		 * $:calendar:lock:status
		 */
		def handleLockStatus(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			ensureTabEditable(tab_id) {
				Json.obj("owner" -> CalendarService.tabStatus(tab_id))
			}
		}

		/**
		 * $:calendar:lock:acquire
		 */
		def handleLockAcquire(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			ensureTabEditable(tab_id) {
				CalendarService.lockTab(tab_id, user.name) map { lock =>
					edit_lock = Some(lock)
					MessageSuccess
				} getOrElse {
					MessageFailure("An error occured while acquiring the edit lock for this tab")
				}
			}
		}

		/**
		 * $:calendar:lock:refresh
		 */
		def handleLockRefresh(arg: JsValue): MessageResponse = {
			edit_lock foreach (_.refresh())
			MessageSuccess
		}

		/**
		 * $:calendar:lock:release
		 */
		def handleLockRelease(arg: JsValue): MessageResponse = {
			edit_lock foreach (_.release())
			MessageSuccess
		}

		/**
		 * $:calendar:upcoming:events
		 */
		def handleUpcomingEvents(arg: JsValue): MessageResponse = {
			val from = SmartTimestamp.today
			val to = from + 15.days

			val events = loadCalendarEvents(from, to).map(_._1)
			Json.obj("events" -> events)
		}
	}
}
