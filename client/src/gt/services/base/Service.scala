package gt.services.base

import scala.concurrent.duration._
import scala.scalajs.js
import scala.scalajs.js.timers._

/**
  * Base trait for Services.
  *
  * Services are singletons instances that provide high-level client-side API
  * to access the server data. Most of the time, services works by setting up
  * caches for received data and automatically managing their state.
  *
  * When a Service is performing cache management for some ressources, its API
  * should return reactive values of the cached data. This way, the Xuen templating
  * system can automatically bind displayed information with the most up-to-date
  * data from the cache.
  *
  * Services will also receive server-sent notifications of updates and update
  * their caches accordingly.
  *
  * Services features an automatic state management allowing it to be automatically
  * enabled when some component on the page are requiring its services and
  * automatically disabled when such components are removed from the page.
  *
  * When a component is disabled, its service channels will be closed and it will
  * not longer receive update notifications from the server. In this case, current
  * cached data should be wiped and lazily refetched on next activation.
  */
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
			delay = setTimeout(5.seconds) {
				if (counter == 0) {
					channels.foreach(c => c.close())
					disable()
				}
			}
		}
	}

	/** Overrides this method to perform actions on service activation */
	protected def enable(): Unit = {}

	/** Overrides this method to perform actions on service deactivation */
	protected def disable(): Unit = {}

	/**
	  * Registers a Service Channel for this Server.
	  *
	  * The implicit Delegate object should usually be provided by extending the
	  * Delegate trait in the Service instance. In such case, the Delegate trait
	  * will provide an implicit reference to itself.
	  *
	  * @param tpe      the type of channel to open
	  * @param lzy      if lazy, the channel will not be open until first required
	  * @param delegate the delegate handling incoming message
	  */
	protected final def registerChannel(tpe: String, lzy: Boolean = true)(implicit delegate: Delegate): ServiceChannel = {
		val channel = new ServiceChannel(tpe, lzy, delegate)
		channels.push(channel)
		channel
	}
}
