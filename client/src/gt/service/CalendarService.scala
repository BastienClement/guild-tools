package gt.service

import boopickle.DefaultBasic._
import gt.service.base.{Cache, Delegate, Service}
import model.calendar.{Answer, Event, Slack}
import scala.collection.immutable.BitSet
import util.DateTime
import xuen.rx.Rx

object CalendarService extends Service with Delegate {
	val channel = registerChannel("calendar")

	/** The set of already requested month keys */
	private var requested = BitSet()

	/**
	  * Ensures that the given month was loaded in the cache.
	  *
	  * The month key is adjusted by an offset of 168 to ensure
	  * optimal performances with BitSet. This means that the system
	  * does not support events before 01/2014.
	  */
	@inline private final def load(key: Int): Unit = {
		val month = key / 32
		val adjusted = month - 168
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
		val byDate = new SimpleIndex(e => e.date.toCalendarKey)

		def forKey(key: Int): Rx[Seq[Event]] = {
			load(key)
			byDate.get(key) ~ (_.toSeq.sorted)
		}
	}

	object answers extends Cache((a: Answer) => (a.user, a.event)) {
		override def default(key: (Int, Int)): Answer = key match {
			case (user, event) =>
				Answer(user, event, DateTime.now, 0, None, None, false)
		}
	}

	object slacks extends Cache((a: Slack) => a.id) {
		val ranges = new RangeIndex(s => (s.from.toCalendarKey, s.to.toCalendarKey))

		def forKey(key: Int): Rx[Set[Slack]] = {
			load(key)
			ranges.containing(key, key)
		}
	}

	/** Clears every caches when the service is suspended */
	override protected def disable(): Unit = {
		requested = requested.empty
		events.clear()
		answers.clear()
		slacks.clear()
	}
}
