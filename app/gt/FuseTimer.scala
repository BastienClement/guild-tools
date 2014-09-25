package gt

import scala.concurrent.duration.FiniteDuration

import Global.ExecutionContext
import Utils.scheduler
import akka.actor.Cancellable

object FuseTimer {
	def create(dur: FiniteDuration)(body: => Unit): FuseTimer = {
		new FuseTimer(dur, () => body)
	}
}

class FuseTimer(val d: FiniteDuration, val body: () => Unit) {
	var fuse: Option[Cancellable] = None

	def start(): Unit = if (fuse.isEmpty) {
		fuse = Some(scheduler.scheduleOnce(d) {
			body()
			fuse = None
		})
	}

	def cancel(): Unit = if (fuse.isDefined) {
		fuse.get.cancel()
		fuse = None
	}

	def restart(): Unit = if (fuse.isDefined) {
		cancel()
		start()
	}
}
