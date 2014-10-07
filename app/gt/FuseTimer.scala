package gt

import akka.actor.Cancellable
import gt.Global.ExecutionContext
import gt.Utils.scheduler

import scala.concurrent.duration.FiniteDuration

object FuseTimer {
	def create(dur: FiniteDuration)(body: => Unit): FuseTimer = new FuseTimer(dur, () => body)
}

class FuseTimer(val d: FiniteDuration, val body: () => Unit) {
	var fuse: Option[Cancellable] = None

	def start(): Unit = {
		if (fuse.isDefined) return
		val f = scheduler.scheduleOnce(d) {
			body()
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
