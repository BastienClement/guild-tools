package gt

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration.DurationInt

import Utils.use
import akka.actor.{ ActorRef, actorRef2Scala }
import api.Message

object Socket {
	val sockets = mutable.Map[String, Socket]()

	@tailrec def create(user: User, handler: ActorRef): Socket = {
		val token = Utils.randomToken()

		// Check uniqueness of this token 
		if (sockets contains token) {
			create(user, handler)
		} else {
			use(new Socket(token, user, handler)) { socket =>
				sockets += (token -> socket)
			}
		}
	}

	def findByID(id: String): Option[Socket] = sockets.get(id)
}

class Socket private (val token: String, val user: User, var handler: ActorRef) {
	var open = true
	val queue = mutable.Queue[Message]()

	val disposeTimeout = FuseTimer.create(30.seconds) {
		dispose()
	}

	def !(m: Message): Unit = {
		if (open) {
			handler ! m
		} else {
			queue.enqueue(m)
		}
	}

	def detach(): Unit = {
		if (open) {
			open = false
			handler = null
			disposeTimeout.start()
		}
	}

	def updateHandler(ref: ActorRef): Unit = {
		if (!open) {
			disposeTimeout.cancel()
			handler = ref
			open = true
			drain()
		}
	}

	def drain(): Unit = {
		queue.dequeueAll(m => open) foreach { m =>
			handler ! m
		}
	}

	def dispose(): Unit = {
		Socket.sockets -= token
		user.removeSocket(this)
	}
}
