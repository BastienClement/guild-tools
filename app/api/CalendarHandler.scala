package api

import java.sql.Timestamp
import java.util.GregorianCalendar
import actors.SocketHandler
import models.{ CalendarEvents, _ }
import models.mysql._
import models.sql._
import play.api.libs.json.{ Json, JsNull, JsValue }
import utils.SmartTimestamp.Implicits._
import scala.collection.mutable
import utils.SmartTimestamp
import java.text.SimpleDateFormat
import java.text.ParseException
import scala.slick.jdbc.JdbcBackend.SessionDef
import actors.CalendarLockManager._
import akka.pattern.ask
import gt.Global.ExecutionContext
import play.api.libs.json.Json.JsValueWrapper

/**
 * Shared calendar-related values
 */
object CalendarHelper {
	val guildies_groups = Set[Int](8, 12, 9, 11)

	val guildAnswersQuery = Compiled { (event_id: Column[Int]) =>
		for {
			(u, a) <- Users leftJoin CalendarAnswers on ((u, a) => u.id === a.user && a.event === event_id)
			if (u.group inSet CalendarHelper.guildies_groups)
		} yield (u, (a.answer.?, a.date.?, a.note, a.char))
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
	self: SocketHandler =>

	object Calendar {
		type EventsAndAnswers = List[(CalendarEvent, Option[Int])]

		/**
		 * Event context
		 */
		var event_id = -1
		var event_editable = false
		var event_tabs = Map[Int, CalendarTab]()
		var edit_lock: Option[CalendarLock] = None

		/**
		 * Reset the event context
		 */
		def resetEventContext() = {
			event_id = -1
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
					if (e.date >= from && e.date <= to) && (e.visibility =!= CalendarVisibility.Private || a.answer.?.isDefined)
				} yield (e, a.answer.?)
				events.list
			}
		}

		/**
		 * Create an event filter for current calendar page
		 */
		def createCalendarFilter(events: Set[Int], from: Timestamp, to: Timestamp): EventFilter = {
			var watched_events = events
			var potential_events = mutable.Map[Int, Event]()

			return {
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
		}

		/**
		 * Helper for load + create event filter
		 */
		def loadCalendarAndCreateFilter(from: Timestamp, to: Timestamp): (EventsAndAnswers, EventFilter) = {
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

			val from = SmartTimestamp.createSQL(year, month - 1, 21)
			val to = SmartTimestamp.createSQL(year, month + 1, 15)

			val (events, filter) = loadCalendarAndCreateFilter(from, to)

			socket.bindEvents(filter)

			MessageResults(eventsToJs(events))
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

			def inRange(a: Int, b: Int, c: Int) = (a >= b && a <= c)

			if (!CalendarVisibility.isValid(visibility) || !inRange(hour, 0, 23) || !inRange(minutes, 0, 59)) {
				return MessageFailure("INVALID_EVENT_DATA")
			}

			if (dates_raw.length > 1) {
				if (!user.officer) return MessageFailure("MULTI_NOT_ALLOWED")
				if (visibility == CalendarVisibility.Announce) return MessageAlert("Creating announces on multiple days is not allowed.")
			}

			if (visibility != CalendarVisibility.Private && !user.officer) return MessageAlert("Members can only create restricted events.")

			val format = new SimpleDateFormat("yyyy-MM-dd")
			val now = SmartTimestamp.now

			def isValidDate(ts: SmartTimestamp): Boolean = {
				val delta = (ts - now).time / 1000
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
		def handleAnswer(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val event_id = (arg \ "event").as[Int]
			val answer = (arg \ "answer").as[Int]
			val note_raw = (arg \ "note").asOpt[String]
			val char = (arg \ "char").asOpt[Int]

			val row = for {
				(e, a) <- CalendarEvents leftJoin CalendarAnswers on ((e, a) => a.event === e.id && a.user === user.id)
				if (e.id === event_id) && (e.visibility =!= CalendarVisibility.Private || a.answer.?.isDefined)
			} yield (e, a.answer.?, a.note, a.char)

			row.firstOption map {
				case (event, old_answer, old_note, old_char) =>
					if (event.visibility == CalendarVisibility.Private && old_answer.isEmpty) return MessageFailure("UNINVITED")
					if (event.state != CalendarEventState.Open) return MessageFailure("CLOSED_EVENT")

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
		def handleDelete(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
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
		def handleEvent(arg: JsValue): MessageResponse = DB.withSession { implicit s =>
			val id = (arg \ "id").as[Int]
			val event = CalendarEvents.filter(_.id === id).first

			if (event.visibility == CalendarVisibility.Announce) {
				return MessageFailure("OPENING_ANNOUNCE")
			}

			// Fetch answers for guild events
			def fetchGuildAnswers = {
				CalendarHelper.guildAnswersQuery(id).list.groupBy(_._1.id.toString).mapValues { list =>
					val user = list(0)._1
					val (a_answer, a_date, a_note, a_char) = list(0)._2

					if (a_answer.isDefined) {
						val a = CalendarAnswer(
							user = user.id,
							event = id,
							date = a_date.get,
							answer = a_answer.get,
							note = a_note,
							char = a_char
						)
						Some(a)
					} else {
						None
					}
				}
			}

			// Fetch answers for non-guild events
			def fetchEventAnswers = {
				CalendarHelper.answersQuery(id).list.groupBy(_._1.id.toString).mapValues { list =>
					Some(list(0)._2)
				}
			}

			// Select the correct answer fetcher for this event
			val answers = {
				if (event.visibility == CalendarVisibility.Guild)
					fetchGuildAnswers
				else
					fetchEventAnswers
			}

			// Extract own answer
			val my_answer = answers.get(user.id.toString)

			// Check invitation in private event
			if (event.visibility == CalendarVisibility.Private && my_answer.isEmpty) return MessageFailure("NOT_INVITED")

			// Record successful event access
			event_id = event.id
			event_editable = (event.owner == user.id) || user.officer

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

			// Event page bindings
			socket.bindEvents {
				case CalendarAnswerCreate(answer) => (answer.event == id)
				case CalendarAnswerUpdate(answer) => (answer.event == id)

				case CalendarEventUpdate(event) => (event.id == id)
				case CalendarEventDelete(id) => (id == id)

				case CalendarTabCreate(tab) => utils.doIf(tab.event == id) {
					event_tabs += (tab.id -> tab)
				}

				case CalendarTabWipe(id) => tabContentIsVisible(id)

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

				case CalendarLockAcquire(tab, _) => (tabContentIsVisible(tab) && event_editable)
				case CalendarLockRelease(tab) => (tabContentIsVisible(tab) && event_editable)
			} onUnbind {
				resetEventContext()
			}

			MessageResults(Json.obj(
				"event" -> event,
				"answers" -> answers,
				"answer" -> my_answer,
				"tabs" -> visible.tabs,
				"slots" -> visible.slots,
				"editable" -> event_editable))
		}

		/**
		 * $:calendar:event:state
		 */
		def handleEventState(arg: JsValue): MessageResponse = {
			val state = (arg \ "state").as[Int]

			if (!CalendarEventState.isValid(state)) return MessageFailure("BAD_STATE")
			if (!event_editable) return MessageFailure("FORBIDDEN")

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
				val query = CalendarEvents.filter(_.id === event_id)
				query.map(e => (e.title, e.desc)).update((title, desc))
				CalendarEvents.notifyUpdate(query.first.copy(title = title, desc = desc))
			}

			MessageSuccess
		}

		/**
		 * $:calendar:comp:set
		 * $:calendar:comp:reset
		 */
		def handleCompSet(arg: JsValue, reset: Boolean = false): MessageResponse = {
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

			DB.withTransaction { implicit s =>
				val max_order = CalendarTabs.filter(_.event === event_id).map(_.order).max.run.get
				val template = CalendarTab(0, event_id, title, None, max_order + 1, false, false)
				val id: Int = (CalendarTabs returning CalendarTabs.map(_.id)) += template
				CalendarTabs.notifyCreate(template.copy(id = id))
			}

			MessageSuccess
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
		def handleTabLock(arg: JsValue, lock: Boolean): MessageResponse = {
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
				utils.defer {
					(LockManager ? LockStatus(tab_id)).mapTo[Option[String]] map { status =>
						MessageResults(Json.obj("owner" -> status))
					}
				}
			}
		}

		/**
		 * $:calendar:lock:acquire
		 */
		def handleLockAcquire(arg: JsValue): MessageResponse = {
			val tab_id = (arg \ "id").as[Int]
			ensureTabEditable(tab_id) {
				utils.defer {
					(LockManager ? LockAcquire(tab_id, user.name)).mapTo[Option[CalendarLock]] map { lock =>
						lock map { l =>
							edit_lock = lock
							MessageSuccess
						} getOrElse {
							MessageAlert("An error occurred while acquiring the edit lock for this tab")
						}
					}
				}
			}
		}

		/**
		 * $:calendar:lock:refresh
		 */
		def handleLockRefresh(): MessageResponse = {
			edit_lock foreach (_.refresh())
			MessageSuccess
		}

		/**
		 * $:calendar:lock:release
		 */
		def handleLockRelease(): MessageResponse = {
			edit_lock foreach (_.release())
			MessageSuccess
		}
	}
}
