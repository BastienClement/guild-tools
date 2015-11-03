package actors

import akka.actor._
import gtp3.Socket.{ForceStop, Opener}
import gtp3.{Socket, WSActor}
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
	private val sockets = Bindings[Long, ActorTag[Socket]]()

	// Close timeouts
	private val timeouts = mutable.Map[ActorTag[Socket], Timeout]()

	// Random number genertor for socket id
	private val rand = new SecureRandom()

	// Access to actor references
	private val context = TypedActor.context
	private val self = TypedActor(context.system).getActorRefFor(TypedActor.self[SocketManager])

	/**
	  * Generates a new socket ID.
	  */
	@tailrec
	private def nextSocketID: Long = {
		val id = rand.nextLong()
		if (id == 0 || sockets.containsSource(id)) nextSocketID
		else id
	}

	/**
	  * Decrements the socket counter for this origin.
	  */
	def disconnected(socket: ActorTag[Socket]): Unit = {
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
	  * Allocates a new socket for a given actor.
	  */
	def allocate(actor: ActorTag[WSActor], opener: Opener): Future[ActorTag[Socket]] = AsFuture {
		val id = nextSocketID

		val socket = context.actorOf(Socket.props(id, opener))
		context.watch(socket)
		sockets.add(id, socket)

		socket ! Socket.Handshake(actor)
		socket
	}

	/**
	  * Rebinds a socket to the given actor and return this socket.
	  */
	def rebind(actor: ActorTag[WSActor], opener: Opener, id: Long, seq: Int): Future[ActorTag[Socket]] = {
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

	/**
	  * Kills an open socket.
	  */
	def killSocket(id: Long) = {
		for (socket <- sockets.getTarget(id)) socket ! ForceStop
	}

	/**
	  * Returns the list of open sockets.
	  */
	def socketsMap = Future.successful(sockets.toMap)

	def onReceive(message: Any, sender: ActorRef) = message match {
		case Terminated(socket) => sockets.removeTarget(socket)
	}
}
