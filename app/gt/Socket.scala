package gt

import api._
import play.api.libs.json._
import akka.actor.{ ActorRef, actorRef2Scala }
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import Global.ExecutionContext
import scala.concurrent.Future

object Socket {
	var sockets = Map[String, Socket]()

	def disposed(socket: Socket): Unit = sockets.synchronized {
		sockets -= socket.token
	}

	def create(user: User, session: String, handler: ActorRef): Socket = {
		@tailrec def loop(): Socket = {
			val token = Utils.randomToken()

			// Check uniqueness of this token 
			if (sockets contains token) {
				loop()
			} else {
				val socket = new Socket(token, user, session, handler)
				sockets += (token -> socket)
				socket
			}
		}

		sockets.synchronized {
			loop()
		}
	}

	def findByID(id: String): Option[Socket] = sockets.get(id)

	def !(m: Message): Unit = Future { sockets.values.par foreach { _ ! m } }
	def !!(e: Event): Unit = Future { sockets.values.par foreach { _ !! e } }
}

class Socket private (val token: String, val user: User, val session: String, var handler: ActorRef) {
	var open = true
	var dead = false

	val queue = mutable.Queue[OutgoingMessage]()

	type EventFilter = PartialFunction[Event, Boolean]
	val FilterNone: EventFilter = { case _ => false }
	var eventFilter: EventFilter = FilterNone

	/**
	 * Socket can be rebound for up to 30 secs after handler death
	 */
	val disposeTimeout = FuseTimer.create(30.seconds) {
		dispose()
	}

	/**
	 * Send message to the socket or enqueue it if not open
	 */
	def !(m: OutgoingMessage): Unit = {
		if (dead) {
			return
		} else if (open) {
			handler ! m
		} else {
			queue.synchronized { queue.enqueue(m) }
		}
	}

	/**
	 * Check if event is this socket listen to an event and send it
	 */
	def !!(e: Event): Unit = {
		if (eventFilter.applyOrElse(e, FilterNone)) {
			this ! Message("event:dispatch", e.asJson)
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
				queue.dequeueAll(m => open) foreach { m =>
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
