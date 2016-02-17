package utils

import akka.actor.Cancellable
import reactive.ExecutionContext
import scala.concurrent.duration.FiniteDuration

object Timeout {
	/**
	  * Construct a timeout
	  * @param dur    Duration of this timeout
	  * @param body   A block to execute when the timeout is over
	  * @return       A Timeout object not yet started
	  */
	def apply(dur: FiniteDuration)(body: => Unit): Timeout = new Timeout(dur)(body)

	/**
	  * Return a started timeout
	  * @param dur    Duration of this timeout
	  * @param body   A block to execute when the timeout is over
	  * @return       A Timeout object already started
	  */
	def start(dur: FiniteDuration)(body: => Unit): Timeout = {
		val t = Timeout(dur)(body)
		t.start()
		t
	}
}

/**
  * A timeout helper
  * @param d      Duration of the timer
  * @param body   A block to execute when the timeout is over
  */
class Timeout(d: FiniteDuration)(body: => Unit) {
	/**
	  * The fuse is the handler returned by the Akka scheduler.
	  * Saving it allows to cancel the registered event once it has been scheduled.
	  */
	private var fuse: Option[Cancellable] = None

	/**
	  * Start the timeout.
	  * Does nothing if the timer was already initiated.
	  * Once the timer is over, the given callback method will be called.
	  * A expired timer can be started again.
	  */
	def start(): Unit = {
		if (fuse.isDefined) return
		val f = scheduler.scheduleOnce(d) {
			body
			fuse = None
		}
		fuse = Some(f)
	}

	/**
	  * Cancel the timeout.
	  * Does nothing is the timer as not yet been initiated or
	  * has already been canceled.
	  * The canceled timer can be restarted later.
	  */
	def cancel(): Unit = {
		if (fuse.isEmpty) return
		fuse.get.cancel()
		fuse = None
	}

	/**
	  * Restart the timeout.
	  * Shortcut for cancel() followed by start()
	  * Does not start the timer if not already initiated.
	  */
	def restart(): Unit = {
		if (fuse.isEmpty) return
		cancel()
		start()
	}

	/**
	  * Instantly trigger the timer callback.
	  * If the timeout was initiated, it is canceled.
	  */
	def trigger(): Unit = {
		cancel()
		body
	}
}
