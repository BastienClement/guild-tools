package actors

import java.security.SecureRandom

import actors.Actors.ActorImplicits
import actors.SocketManager._
import akka.actor._
import gtp3.Socket
import utils.{Bindings, Timeout}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._

object SocketManager {
	case class Handshake()
	case class Resume(seq: Int)

	private case class CloseSocket(socket: ActorRef)
	private case class SetTarget(target: ActorRef)

	private class SocketProxy extends Actor {
		private var target: ActorRef = context.system.deadLetters
		def receive = {
			case SetTarget(t) => target = t
			case m => target ! m
		}
	}
}

trait SocketManager extends TypedActor.Receiver with ActorImplicits {
	// Open sockets
	private val sockets = Bindings[Long, ActorRef]()
	private val bindings = Bindings[ActorRef, ActorRef]()
	private val proxies = mutable.Map[ActorRef, ActorRef]()

	// Close timeouts
	private val timeouts = mutable.Map[ActorRef, Timeout]()

	// Counter of open socket for each origin
	private val remote_count = mutable.Map[String, Int]()

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
	 * Ensure that nobody is trying to open a tremendous amount of sockets
	 */
	def accept(remote: String): Boolean = {
		val new_count = remote_count.get(remote) map (_ + 1) getOrElse 1
		if (new_count > 15) {
			false
		} else {
			remote_count(remote) = new_count
			true
		}
	}

	/**
	 * Decrement the socket counter for this origin
	 */
	def disconnected(actor: ActorRef, remote: String): Unit = {
		for (count <- remote_count.get(remote)) {
			if (count == 1) remote_count.remove(remote)
			else remote_count(remote) = count - 1
		}

		bindings.getTarget(actor) match {
			case Some(socket) =>
				for (proxy <- proxies.get(socket))
					proxy ! SetTarget(context.system.deadLetters)

				val timeout = Timeout(15.seconds) {
					self ! CloseSocket(socket)
				}

				timeouts += (socket -> timeout)
				timeout.start()

			case None => // Nothing
		}

		bindings.removeSource(actor)
	}

	/**
	 * Allocate a new socket for a given actor
	 */
	def allocate(actor: ActorRef): Future[ActorRef] = {
		val id = nextSocketID

		val proxy = context.actorOf(Props(new SocketProxy))
		proxy ! SetTarget(actor)

		val socket = context.actorOf(Socket.props(id, proxy))
		context.watch(socket)

		sockets.add(id, socket)
		bindings.add(actor, socket)
		proxies += socket -> proxy

		socket ! Handshake()
		socket
	}

	/**
	 * Rebind a socket to the given actor and return this socket
	 */
	def rebind(actor: ActorRef, id: Long, seq: Int): Future[ActorRef] = {
		sockets.getTarget(id) match {
			case Some(socket) =>
				for (proxy <- proxies.get(socket))
					proxy ! SetTarget(actor)

				for (timeout <- timeouts.get(socket)) {
					timeout.cancel()
					timeouts.remove(socket)
				}

				socket ! Resume(seq)
				socket

			case None => allocate(actor)
		}
	}

	def onReceive(message: Any, sender: ActorRef) = message match {
		case SocketManager.CloseSocket(socket) =>
			if (!bindings.containsTarget(socket)) {
				context.stop(socket)
			}

		case Terminated(actor) =>
			for (ws <- bindings.getSource(sender))
				ws ! Kill
			for (proxy <- proxies.get(actor))
				context.stop(proxy)
			bindings.removeTarget(actor)
			sockets.removeTarget(actor)
			proxies.remove(actor)
	}
}

class SocketManagerImpl extends SocketManager
