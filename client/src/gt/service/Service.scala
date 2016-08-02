package gt.service

import gtp3.PickledPayload
import org.scalajs.dom.console
import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.timers._

trait Service {
	/** Number of component currently using the service */
	private[this] var counter = 0

	/** Disable delay timer handler */
	private[this] var delay: SetTimeoutHandle = null

	/** Service channels of this service */
	private[this] val channels = js.Array[ServiceChannel]()

	/** Enables the service when a component requiring it is attached */
	final def acquire(): Unit = {
		counter += 1
		if (counter == 1) {
			clearTimeout(delay)
			channels.foreach(c => c.eagerOpen())
			enable()
		}
	}

	/** Releases the service when a component requiring it is detached */
	final def release(): Unit = {
		counter -= 1
		if (counter == 0) {
			clearTimeout(delay)
			delay = setTimeout(2.seconds) {
				if (counter == 0) {
					channels.foreach(c => c.close())
					disable()
				}
			}
		}
	}

	protected def enable(): Unit = {}
	protected def disable(): Unit = {}

	protected type Delegate = ((String, PickledPayload)) => Unit

	private def defaultDelegate: Delegate = {
		case (message, _) => console.warn(s"Ignored received message of type $message")
	}

	protected final def registerChannel(tpe: String, lzy: Boolean = true,
	                                    delegate: Delegate = defaultDelegate): ServiceChannel = {
		val channel = new ServiceChannel(tpe, lzy)(delegate)
		channels.push(channel)
		channel
	}
}
