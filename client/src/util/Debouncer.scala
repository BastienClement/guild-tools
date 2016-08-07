package util

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.scalajs.js.timers.{SetTimeoutHandle, clearTimeout, setTimeout}

case class Debouncer[T](delay: FiniteDuration)(block: => T) {
	private[this] var deadline = Deadline.now
	private[this] var scheduled: SetTimeoutHandle = null

	def trigger(): Unit = {
		deadline = delay.fromNow
		if (scheduled == null) schedule()
	}

	def now(): Unit = {
		if (scheduled != null) clearTimeout(scheduled)
		block
	}

	private def schedule(): Unit = {
		scheduled = setTimeout(deadline.timeLeft) {
			scheduled = null
			if (deadline.isOverdue) block
			else schedule()
		}
	}
}
