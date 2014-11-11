package gt

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import akka.actor.{ActorRef, actorRef2Scala}
import api._
import utils.Timeout

object Socket {
	var sockets = Map[String, Socket]()

	def disposed(socket: Socket): Unit = this.synchronized {
		sockets -= socket.token
	}

	def create(user: User, session: String, handler: ActorRef): Socket = this.synchronized {
		@tailrec def loop(): Socket = {
			val token = utils.randomToken()

			// Check uniqueness of this token
			if (sockets contains token) {
				loop()
			} else {
				val socket = new Socket(token, user, session, handler)
				sockets += (token -> socket)
				socket
			}
		}

		loop()
	}

	def findByID(id: String): Option[Socket] = sockets.get(id)

	def !(e: Message): Unit = sockets.values foreach { _ ! e }
	def !#(e: Event): Unit = sockets.values foreach { _ ! e }
}

class CleanupHandler() {
	var handler: Option[() => Unit] = None
	def onUnbind(fn: => Unit): Unit = { handler = Some(() => fn) }
	def apply(): Unit = { handler foreach (_()) }
}

class Socket private(val token: String, val user: User, val session: String, var handler: ActorRef) {
	private var open = true
	private var dead = false

	private val queue = mutable.Queue[AnyRef]()

	/**
	 * Event management
	 */
	type EventFilter = PartialFunction[Event, Boolean]
	private val FilterNone: EventFilter = {case _ => false }
	private var eventFilter: EventFilter = FilterNone
	private var eventCleanup: Option[CleanupHandler] = None
	private var eventObject: Event = null
	private var additionalEvents: List[Event] = Nil

	def unbindEvents(): Unit = {
		eventFilter = FilterNone
		if (eventCleanup.isDefined) {
			eventCleanup.get.apply()
			eventCleanup = None
		}
	}

	def bindEvents(filter: EventFilter) = {
		unbindEvents()
		eventFilter = filter
		val cleanup = new CleanupHandler()
		eventCleanup = Some(cleanup)
		cleanup
	}

	def !<(e: Event): Boolean = {
		eventObject = e
		true
	}

	def !~(e: Event): Boolean = {
		additionalEvents ::= e
		true
	}

	/**
	 * Check if event is this socket listen to an event and send it
	 */
	def handleEvent(e: Event): Unit = this.synchronized {
		eventObject = e
		additionalEvents = Nil

		if (eventFilter.applyOrElse(e, FilterNone)) {
			additionalEvents ::= eventObject
		}

		for (event <- additionalEvents) {
			handler ! Message("event:dispatch", event.asJson)
		}
	}

	/**
	 * Socket can be rebound for up to 30 secs after handler death
	 */
	private val disposeTimeout = Timeout(30.seconds) {
		dispose()
	}

	/**
	 * Send message to the socket or enqueue it if not open
	 */
	def !(m: AnyRef): Unit = {
		if (dead) {
			// Nothing
		} else if (open) {
			handler ! m
		} else {
			queue.synchronized { queue.enqueue(m) }
		}
	}

	/**
	 * Detach the handler from this socket and start the timeout
	 */
	def detach(): Unit = {
		if (open && !dead) {
			open = false
			handler = null
			disposeTimeout.start()
		}
	}

	/**
	 * Attach a new handler for this socket and stop the timeout
	 */
	def updateHandler(ref: ActorRef): Unit = {
		if (!open && !dead) {
			disposeTimeout.cancel()
			handler = ref
			open = true

			// Send queued messages
			queue.synchronized {
				for (m <- queue.dequeueAll(m => open)) {
					handler ! m
				}
			}
		}
	}

	/**
	 * Forcefully close this socket, it cannot be rebound
	 */
	def close(reason: String): Unit = {
		if (dead) return
		this ! CloseMessage(reason)
		dispose()
	}

	/**
	 * Dispose this socket
	 */
	def dispose(): Unit = {
		if (dead) return
		dead = true
		Socket.disposed(this)
		user.removeSocket(this)
	}
}
