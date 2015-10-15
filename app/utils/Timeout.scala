package utils

import akka.actor.Cancellable
import reactive.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Timeout {
	// Construct a timeout
	def apply(dur: FiniteDuration)(body: => Unit): Timeout = new Timeout(dur)(body)

	// Return a started timeout
	def start(dur: FiniteDuration)(body: => Unit): Timeout = {
		val t = Timeout(dur)(body)
		t.start()
		t
	}
}

class Timeout(d: FiniteDuration)(body: => Unit) {
	private var fuse: Option[Cancellable] = None

	def start(): Unit = {
		if (fuse.isDefined) return
		val f = scheduler.scheduleOnce(d) {
			body
			fuse = None
		}
		fuse = Some(f)
	}

	def cancel(): Unit = {
		if (fuse.isEmpty) return
		fuse.get.cancel()
		fuse = None
	}

	def restart(): Unit = {
		if (fuse.isEmpty) return
		cancel()
		start()
	}

	def trigger(): Unit = {
		cancel()
		body
	}
}
