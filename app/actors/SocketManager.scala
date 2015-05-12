package actors

import java.security.SecureRandom
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future
import actors.Actors.Implicits._
import gtp3.{Socket, SocketActor}

trait SocketManager {
	def accept(actor: SocketActor): Boolean
	def disconnected(actor: SocketActor): Unit

	def allocate(actor: SocketActor): Future[Socket]
	def rebind(actor: SocketActor, id: Long, seq: Int): Future[Socket]

	def close(socket: Socket): Unit
}

class SocketManagerImpl extends SocketManager {
	// Open sockets
	private val sockets = mutable.Map[Long, Socket]()

	// Counter of open socket for each origin
	private val remote_count = mutable.Map[String, Int]()

	// Random number genertor for socket id
	private val rand = new SecureRandom()

	/**
	 * Generate a new socket ID
	 */
	@tailrec
	private def nextSocketID: Long = {
		val id = rand.nextLong()
		if (id == 0 || sockets.contains(id)) nextSocketID
		else id
	}

	/**
	 * Ensure that nobody is trying to open a tremendous amount of sockets
	 */
	def accept(actor: SocketActor): Boolean = {
		val remote = actor.remote
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
	def disconnected(actor: SocketActor): Unit = {
		val remote = actor.remote
		for (count <- remote_count.get(remote)) {
			if (count == 1) remote_count.remove(remote)
			else remote_count(remote) = count - 1
		}
	}

	/**
	 * Allocate a new socket for a given actor
	 */
	def allocate(actor: SocketActor): Future[Socket] = {
		val id = nextSocketID
		val socket = new Socket(id, actor)
		socket
	}

	/**
	 * Rebind a socket to the given actor and return this socket
	 */
	def rebind(actor: SocketActor, id: Long, seq: Int): Future[Socket] = {
		sockets.get(id) match {
			case Some(socket) if !socket.isDead =>
				socket.rebind(actor, seq)
				socket

			case _ =>
				allocate(actor)
		}
	}

	/**
	 * Unregister the socket if left detached for too long
	 */
	def close(socket: Socket): Unit = sockets.remove(socket.id)
}
