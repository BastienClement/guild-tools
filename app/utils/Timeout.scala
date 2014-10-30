package utils

import scala.concurrent.duration.FiniteDuration
import akka.actor.Cancellable
import gt.Global.ExecutionContext

object Timeout {
	def apply(dur: FiniteDuration)(body: => Unit): Timeout = new Timeout(dur)(body)
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
}
