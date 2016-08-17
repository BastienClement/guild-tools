package gt.service

import boopickle.DefaultBasic._
import gt.App
import gt.service.base.{Cache, Delegate, Service}
import model.Toon
import model.calendar.{Answer, Event, Slack}
import rx.Rx
import scala.collection.immutable.BitSet

/**
  * The calendar services is responsible for handling calendar and slacks
  * related taks. This include keeping local caches of events, answers and
  * slacks and providing high-level API for manipulating calendar data.
  */
object CalendarService extends Service with Delegate {
	/** The calendar service channel */
	val channel = registerChannel("calendar")

	/** The set of already requested month keys */
	private var monthRequested = BitSet()

	/** The set of events already requested */
	private var answersRequested = Set[Int]()

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
	  * genereated to prevent a trigger of the full event data
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
	}

	/**
	  * The cache of Answers objects
	  */
	object answers extends Cache((a: Answer) => (a.user, a.event)) {
		/** Answers groupping by events */
		val byEvent = new SimpleIndex(e => e.event)

		/** Constructs a default, synthethic, answer */
		def constructDefault(user: Int, event: Int): Answer = Answer(user, event, null, 0, None, None, false)

		/** Insert the default answer in the cache */
		def insertDefault(user: Int, event: Int): Unit = update(constructDefault(user, event))

		/** Automatically query server when accessing undefined answers */
		override def default(key: (Int, Int)): Answer = key match {
			case (user, event) =>
				loadAnswers(event)
				constructDefault(user, event)
		}

		/** Queries answers for a given event */
		def forEvent(event: Int): Rx[Set[Answer]] = {
			loadAnswers(event)
			byEvent.get(event)
		}

		def myAnswerForEvent(event: Int): Rx[Answer] = get((App.user.id, event))
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
	  * Changes the user's answer to an event.
	  *
	  * @param event   the event id
	  * @param answer  the answer value
	  * @param message an optional details message
	  * @param toon    an optional toon to associate with the answer
	  */
	def changeEventAnswer(event: Int, answer: Int, message: Option[String] = None, toon: Option[Toon] = None): Unit = {
		channel.send("change-event-answer", (event, answer, message, toon))
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

	message("event-updated")(events.update _)
	message("event-deleted")(events.remove _)
	message("answer-updated")(answers.update _)

	/** Clears every caches when the service is suspended */
	override protected def disable(): Unit = {
		monthRequested = monthRequested.empty
		answersRequested = answersRequested.empty
		events.clear()
		answers.clear()
		slacks.clear()
	}
}
