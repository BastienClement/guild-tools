package actors

import akka.actor._
import gtp3.Socket
import gtp3.Socket.Opener
import java.security.SecureRandom
import reactive.AsFuture
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.{Bindings, Timeout}

private[actors] class SocketManagerImpl extends SocketManager

object SocketManager extends StaticActor[SocketManager, SocketManagerImpl]("SocketManager")

trait SocketManager extends TypedActor.Receiver {
	// Open sockets
	private val sockets = Bindings[Long, ActorRef]()

	// Close timeouts
	private val timeouts = mutable.Map[ActorRef, Timeout]()

	// Random number genertor for socket id
	private val rand = new SecureRandom()

	// Access to actor references
	private val context = TypedActor.context
	private val self = TypedActor(context.system).getActorRefFor(TypedActor.self[SocketManager])

	/**
	 * Generate a new socket ID
	 */
	@tailrec
	private def nextSocketID: Long = {
		val id = rand.nextLong()
		if (id == 0 || sockets.containsSource(id)) nextSocketID
		else id
	}

	/**
	 * Decrement the socket counter for this origin
	 */
	def disconnected(socket: ActorRef): Unit = {
		sockets.getSource(socket) match {
			case Some(id) =>
				socket ! Socket.Disconnect
				timeouts += socket -> Timeout.start(15.seconds) {
					socket ! PoisonPill
				}

			case None =>
			// The socket does not exists ???
		}
	}

	/**
	 * Allocate a new socket for a given actor
	 */
	def allocate(actor: ActorRef, opener: Opener): Future[ActorRef] = AsFuture {
		val id = nextSocketID

		val socket = context.actorOf(Socket.props(id, opener))
		context.watch(socket)
		sockets.add(id, socket)

		socket ! Socket.Handshake(actor)
		socket
	}

	/**
	 * Rebind a socket to the given actor and return this socket
	 */
	def rebind(actor: ActorRef, opener: Opener, id: Long, seq: Int): Future[ActorRef] = {
		sockets.getTarget(id) match {
			case Some(socket) => AsFuture {
				for (timeout <- timeouts.get(socket)) {
					timeout.cancel()
					timeouts.remove(socket)
				}

				socket ! Socket.Resume(actor, seq)
				socket
			}

			case None => allocate(actor, opener)
		}
	}

	def onReceive(message: Any, sender: ActorRef) = message match {
		case Terminated(socket) => sockets.removeTarget(socket)
	}
}
