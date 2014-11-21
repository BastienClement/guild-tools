package actors

import akka.actor.{ActorRef, Terminated, TypedActor}

/**
 * Base trait for anything that can be dispatched to listeners
 */
trait Dispatchable

/**
 * Base interface for plain object event handler
 */
trait EventListener {
	def onEvent(event: Dispatchable): Unit
}

/**
 * Public interface
 */
trait Dispatcher {
	/**
	 * Register a new event listener interested in generated events
	 * Can be both a plain obejct handler or an Akka actor
	 */
	def register(listener: EventListener): Unit
	def register(listener: ActorRef): Unit

	/**
	 * Remove a previously registered listener
	 */
	def unregister(listener: EventListener): Unit
	def unregister(listener: ActorRef): Unit

	/**
	 * Perform event dispatch
	 */
	def !# (event: Dispatchable): Unit
}

/**
 * Actor implementation
 */
class DispatcherImpl extends Dispatcher with TypedActor.Receiver {
	private var listeners = Set[AnyRef]()

	def register(listener: EventListener): Unit = listeners += listener
	def unregister(listener: EventListener): Unit = listeners -= listener

	def register(listener: ActorRef): Unit = {
		TypedActor.context.watch(listener)
		listeners += listener
	}

	def unregister(listener: ActorRef): Unit = {
		TypedActor.context.unwatch(listener)
		listeners -= listener
	}

	def !# (event: Dispatchable): Unit = {
		listeners.par.foreach {
			case listener: EventListener =>
				try {
					listener.onEvent(event)
				} catch {
					// Automatically remove troublesome listeners
					case e: Throwable => listeners -= listener
				}

			case actor: ActorRef =>
				actor ! event
		}
	}

	/**
	 * Receive actor termination notice
	 */
	override def onReceive(message: Any, sender: ActorRef): Unit = message match {
		case Terminated(actor) => unregister(actor)
		case _ => // nothing
	}
}
