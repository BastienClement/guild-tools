package gt.component

import gt.App
import gt.service.base.Service
import scala.collection.mutable
import util.jsannotation.js
import xuen.Handler

/**
  * Common additions to every component handler in GuildTools
  */
@js abstract class GtHandler extends Handler {
	/** Alias to the App object */
	final val app: App.type = App

	/** The Set of Services used by this component */
	private[this] val usedServices = mutable.Set[Service]()

	/**
	  * Registers a service as used by this component.
	  *
	  * The service will be automatically acquired when the component is
	  * attached and released when the component is detached.
	  *
	  * @param service the service used
	  */
	protected def useService(service: Service): Unit = usedServices.add(service)
	protected def useServices(services: Service*): Unit = services.foreach(useService)

	protected def service[S <: Service](service: S): S = { useService(service); service }

	override def attached(): Unit = {
		for (service <- usedServices) service.acquire()
	}

	override def detached(): Unit = {
		for (service <- usedServices) service.release()
	}
}
