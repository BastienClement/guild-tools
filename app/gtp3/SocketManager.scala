package gtp3

import java.security.SecureRandom

import actors.Actors.Implicits._

import scala.collection.mutable
import scala.concurrent.Future

object SocketManager {
	private val sockets = mutable.Map[Long, Socket]()
	private val remote_count = mutable.Map[String, Int]()

	private val rand = new SecureRandom()

	/**
	 * Generate a new socket ID
	 */
	private def nextSocketID: Long = this.synchronized {
		var id: Long = 0
		do {
			id = rand.nextLong()
		} while (sockets contains id)
		id
	}

	/**
	 * Ensure that nobody is trying to open a tremendous amount of sockets
	 */
	def validate(actor: SocketActor): Boolean = this.synchronized {
		val remote = actor.request.remoteAddress
		val new_count = remote_count.get(remote) map (_ + 1) getOrElse 1
		if (new_count > 25) {
			false
		} else {
			remote_count(remote) = new_count
			true
		}
	}

	/**
	 * Decrement the socket counter for this origin
	 */
	def closed(actor: SocketActor): Unit = this.synchronized {
		val remote = actor.request.remoteAddress
		for (count <- remote_count.get(remote)) {
			if (count == 1) remote_count.remove(remote)
			else remote_count(remote) = count - 1
		}
	}

	def allocate(actor: SocketActor): Future[Socket] = this.synchronized {
		val id = nextSocketID
		val socket = new Socket(id, actor)
		socket.attach(actor)
		socket
	}

	def rebind(actor: SocketActor, id: Long, seq: Int): Future[Socket] = {
		???
	}
}
