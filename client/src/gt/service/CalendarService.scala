package gt.service

import boopickle.DefaultBasic._
import gt.service.base.{Cache, Delegate, Service}
import model.calendar.{Answer, Event, Slack}
import scala.collection.immutable.BitSet
import util.DateTime
import xuen.rx.Rx

object CalendarService extends Service with Delegate {
	val channel = registerChannel("calendar")

	type MonthKey = Int
	type DayKey = Int
	case class Key(month: MonthKey, day: DayKey)

	/** The set of already requested month keys */
	private var requested = BitSet()

	/**
	  * Ensures that the given month was loaded in the cache.
	  *
	  * The month key is adjusted by an offset of 24167 to ensure
	  * optimal performances with BitSet. This means that the system
	  * does not support events before 01/2014.
	  *
	  * @param month the month to load
	  */
	@inline private final def load(month: MonthKey): Unit = {
		val adjusted = month - 24167
		if (adjusted > 0 && !requested(adjusted)) {
			requested += adjusted
			channel.request("load-month", month).apply(loadData _)
		}
	}

	private def loadData(e: Seq[(Event, Option[Answer])], s: Seq[Slack]) = {
		Rx.atomically {
			for ((event, answer) <- e) {
				events.update(event)
				answer.foreach(answers.update)
			}
			for (slack <- s) {
				slacks.update(slack)
			}
		}
	}

	object events extends Cache((e: Event) => e.id) {
		val byDate = new Index(e => e.fullKey)

		def forKey(key: Key): Rx[Seq[Event]] = {
			load(key.month)
			byDate.get((key.month, key.day)) ~ (_.toSeq.sorted)
		}
	}

	object answers extends Cache((a: Answer) => (a.user, a.event)) {
		override def default(key: (Int, Int)): Answer = key match {
			case (user, event) =>
				Answer(user, event, DateTime.now, 0, None, None, false)
		}
	}

	object slacks extends Cache((a: Slack) => a.id) {

	}

	def eventsForKey(key: Key): Rx[Seq[Event]] = Nil
	def slacksForKey(key: Key): Rx[Seq[Slack]] = Nil

	/** Clears every caches when the service is suspended */
	override protected def disable(): Unit = {
		requested = requested.empty
		events.clear()
		answers.clear()
		slacks.clear()
	}
}
