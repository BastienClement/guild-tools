package gt

import Utils.using
import akka.actor.{ ActorRef, actorRef2Scala }
import api.{ CloseMessage, OutgoingMessage }
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

object Socket {
	val sockets = mutable.Map[String, Socket]()

	@tailrec def create(user: User, session: String, handler: ActorRef): Socket = {
		val token = Utils.randomToken()

		// Check uniqueness of this token 
		if (sockets contains token) {
			create(user, session, handler)
		} else {
			using(new Socket(token, user, session, handler)) { socket =>
				sockets += (token -> socket)
			}
		}
	}

	def findByID(id: String): Option[Socket] = sockets.get(id)
}

class Socket private (val token: String, val user: User, val session: String, var handler: ActorRef) {
	/**
	 * Socket is open as long as the websocket handler is alive
	 */
	var open = true

	/**
	 * Socket is dead when disposed
	 */
	var dead = false

	/**
	 * Message queue if handler is temporarily offline
	 */
	val queue = mutable.Queue[OutgoingMessage]()

	/**
	 * Socket can be rebound for up to 30 secs after handler death
	 */
	val disposeTimeout = FuseTimer.create(30.seconds) {
		dispose()
	}
	
	var boundEvents = Set[String]()

	/**
	 * Send message to the socket or enqueue it if not open
	 */
	def !(m: OutgoingMessage): Unit = {
		if (dead) {
			return
		} else if (open) {
			handler ! m
		} else {
			queue.enqueue(m)
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
			queue.dequeueAll(m => open) foreach { m =>
				handler ! m
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
		Socket.sockets -= token
		user.removeSocket(this)
		dead = true
	}
}
