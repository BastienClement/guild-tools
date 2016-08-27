package gt.services

import boopickle.DefaultBasic._
import gt.App
import gt.services.base.{Cache, Delegate, Service}
import models.calendar._
import rx.Rx
import scala.collection.immutable.BitSet
import scala.concurrent.Future
import utils.DateTime

/**
  * The calendar services is responsible for handling calendar and slacks
  * related tasks. This include keeping local caches of events, answers and
  * slacks and providing high-level API for manipulating calendar data.
  */
object CalendarService extends Service with Delegate {
	/** The calendar service channel */
	val channel = registerChannel("calendar")

	/** The set of already requested month keys */
	private var monthRequested = BitSet()

	/** The set of events already requested */
	private var answersRequested = Set[Int]()

	/** The set of deleted events */
	private var eventsDeleted = Set[Int]()

	/**
	  * Ensures that the given month was loaded in the cache.
	  *
	  * The month key is adjusted by an offset of 168 to ensure
	  * optimal performances with BitSet. This means that the system
	  * does not support events before 01/2014.
	  */
	private def loadMonth(key: Int): Unit = {
		val month = key / 32
		val adjusted = month - 168
		if (adjusted > 0 && !monthRequested(adjusted)) {
			monthRequested += adjusted
			channel.request("load-month", month)(loadData _)
		}
	}

	/**
	  * Imports data received from load in caches.
	  *
	  * If there is no answer for an event, a default one is
	  * generated to prevent a trigger of the full event data
	  * when displayed in the calendar.
	  *
	  * @param e events and answers data
	  * @param s slacks data
	  */
	private def loadData(e: Seq[(Event, Option[Answer])], s: Seq[Slack]) = {
		Rx.atomically {
			for ((event, answer) <- e) {
				events.update(event)
				answer match {
					case Some(a) => answers.update(a)
					case None => answers.insertDefault(App.user.id, event.id)
				}
			}
			for (slack <- s) {
				slacks.update(slack)
			}
		}
	}

	/**
	  * Loads answers for the given event.
	  *
	  * @param event the event for which answers should be loaded
	  */
	private def loadAnswers(event: Int): Unit = if (!answersRequested.contains(event)) {
		answersRequested += event
		channel.request("event-answers", event) { as: Seq[Answer] =>
			as.foreach(answers.update)
		}
	}

	/**
	  * The cache of Events objects
	  */
	object events extends Cache((e: Event) => e.id) {
		/** Events by date key */
		val byDate = new SimpleIndex(e => e.date.toCalendarKey)

		/** Constructs and requests unknown events */
		override def default(eventid: Int): Event = {
			if (eventid > 0 && !eventsDeleted.contains(eventid)) {
				channel.request("load-event", eventid) { ev: Option[Event] =>
					ev match {
						case Some(e) =>
							update(e)
							// Ensure that the whole month of this event is loaded to have slacks data
							loadMonth(e.date.toCalendarKey)
						case None => removeKey(eventid)
					}
				}
			}
			Event(eventid, s"Event#$eventid", "", 0, DateTime.now, 0, EventVisibility.Restricted, EventState.Open)
		}

		/**
		  * Returns the sorted list of event for a given day.
		  *
		  * If the data for the requested day is not yet loaded,
		  * a request is send to the server for the whole month.
		  *
		  * @param key the day key to query
		  */
		def forKey(key: Int): Rx[Seq[Event]] = {
			loadMonth(key)
			byDate.get(key) ~ (_.toSeq.sorted)
		}

		/**
		  * Checks event existence on the server-side.
		  *
		  * This function returns false if the event exists but is
		  * not accessible to the current user.
		  *
		  * @param key the event id
		  */
		def exists(key: Int): Future[Boolean] = {
			if (key > 0) channel.request("event-exists", key).as[Boolean]
			else Future.successful(false)
		}

		def deleted(key: Int): Boolean = eventsDeleted.contains(key)
	}

	/**
	  * The cache of Answers objects
	  */
	object answers extends Cache((a: Answer) => (a.user, a.event)) {
		/** Answers grouped by events */
		val byEvent = new SimpleIndex(e => e.event)

		/** Constructs a default, synthetic, answer */
		def constructDefault(user: Int, event: Int): Answer = Answer(user, event, DateTime.zero, 0, None, None, false)

		/** Insert the default answer in the cache */
		def insertDefault(user: Int, event: Int): Unit = update(constructDefault(user, event))

		/** Automatically query server when accessing undefined answers */
		override def default(key: (Int, Int)): Answer = key match {
			case (user, event) =>
				if (event > 0) loadAnswers(event)
				constructDefault(user, event)
		}

		/**
		  * Removes every answer linked to the given event.
		  *
		  * This method should be called when the event is removed and
		  * all answers are automatically dropped from the database.
		  *
		  * @param event the event for which answers should be removed
		  */
		private[CalendarService] def removeForEvent(event: Int): Unit = prune(a => a.event == event)

		/**
		  * Queries answers for a given event.
		  *
		  * Undefined/pending answers for guilder-roster members will not
		  * be automatically generated by this method.
		  *
		  * @param event the event for which the answer are requested
		  * @return the set of answer for this event
		  */
		def forEvent(event: Int): Rx[Set[Answer]] = {
			loadAnswers(event)
			byEvent.get(event)
		}

		/**
		  * Queries the answer of the current user for the given event.
		  *
		  * If there is no answer for this event by the user, the default
		  * dummy answer is returned.
		  *
		  * @param event the event for which the answer should be fetched
		  * @return the answer of the current user to the event
		  */
		def mineForEvent(event: Int): Rx[Answer] = get((App.user.id, event))
	}

	/**
	  * The cache of Slacks objects
	  */
	object slacks extends Cache((a: Slack) => a.id) {
		/** Index of slacks by ranges */
		val ranges = new RangeIndex(s => (s.from.toCalendarKey, s.to.toCalendarKey))

		/** Load slacks matching a given calendar key */
		def forKey(key: Int): Rx[Set[Slack]] = {
			loadMonth(key)
			ranges.containing(key, key)
		}
	}

	/**
	  * Creates a new event from the given template for each day in the given set.
	  *
	  * Creating an event for more than one day requires promoted privileges
	  * on the server-side. Some fields in the given template are automatically
	  * replaced upon reception on the server:
	  *   - `id` will be the database-generated unique id
	  *   - `owner` will be the current logged-in user
	  *   - `state` will be open
	  *
	  * @param template the event template
	  * @param dates    the dates on which the event will occur
	  */
	def createEvent(template: Event, dates: Set[DateTime]): Unit = {
		channel.send("create-event", (template, dates))
	}

	/**
	  * Updates an event.
	  *
	  * @param event the new event data
	  */
	def updateEvent(event: Event): Unit = {
		channel.send("update-event", event)
	}

	/**
	  * Changes the user's answer to an event.
	  *
	  * @param event  the event id
	  * @param answer the answer value
	  * @param toon   an optional toon to associate with the answer
	  * @param note   an optional details message
	  */
	def changeEventAnswer(event: Int, answer: Int, toon: Option[Int] = None, note: Option[String] = None): Unit = {
		channel.send("change-event-answer", (event, answer, toon, note))
	}

	/**
	  * Changes the state of an event.
	  *
	  * @param event the event id
	  * @param state the new state value
	  */
	def changeEventState(event: Int, state: Int): Unit = {
		channel.send("change-event-state", (event, state))
	}

	/**
	  * Deletes an event.
	  *
	  * @param event the event id
	  */
	def deleteEvent(event: Int): Unit = {
		channel.send("delete-event", event)
	}

	// Message bindings
	message("event-updated")(events.update _)
	message("answer-updated")(answers.update _)
	message("event-deleted") { (id: Int) =>
		eventsDeleted += id
		events.removeKey(id)
		answers.removeForEvent(id)
	}

	/** Clears every caches when the service is suspended */
	override protected def disable(): Unit = {
		monthRequested = monthRequested.empty
		answersRequested = answersRequested.empty
		events.clear()
		answers.clear()
		slacks.clear()
	}
}
