package gtp3

import actors.Actors._
import akka.actor.{Actor, ActorRef, PoisonPill}
import gtp3.Socket.{IncomingFrame, OutgoingFrame}
import reactive.ExecutionContext
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import utils.Timeout
import WSActor.BindingFailed

object WSActor {
	// A failed future used when socket binding is obviously failed
	val BindingFailed = Future.failed(null)

	// Number of socket open for each origin
	private val remote_count = mutable.Map[String, Int]()

	// Check that a specific remote adresse does not attempt to open too many sockets
	private def accept(remote: String) = synchronized {
		remote_count.get(remote) match {
			// More than 10 open socket
			case Some(i) if i >= 10 => false

			// Less than 10 open socket
			case Some(i) =>
				remote_count(remote) = i + 1
				true

			// 0 open socket
			case None =>
				remote_count(remote) = 1
				true
		}
	}

	// Decrement the socket counter for a remote
	private def closed(remote: String) = synchronized {
		remote_count.get(remote) match {
			case Some(i) if i == 1 => remote_count.remove(remote)
			case Some(i) => remote_count(remote) = i - 1
			case None => throw new Exception("Attempt to close a socket for a remote without open socket")
		}
	}
}

class WSActor(val out: ActorRef, val remote: String) extends Actor {
	// The attached socket object
	var socket: ActorRef = null

	// Instantly kill socket if too many are being created from one IP address
	if (!WSActor.accept(remote)) {
		self ! PoisonPill
	}

	// Handshake must happen within 20 seconds
	val timeout = Timeout.start(20.seconds) {
		self ! PoisonPill
	}

	/**
	 * Handshake reception
	 */
	def receive = {
		case buffer: Array[Byte] => {
			// Parse the handshake frame
			val status = Frame.decode(buffer) match {
				// Create a new socket for this client
				case HelloFrame(magic, version) =>
					if (magic == GTP3Magic) SocketManager.allocate(self)
					else BindingFailed

				// Rebind an existing socket
				case ResumeFrame(sockid, seq) => SocketManager.rebind(self, sockid, seq)

				// Bad stuff
				case _ => BindingFailed
			}

			context.become(bound)

			status onComplete {
				case Success(s) =>
					timeout.cancel()
					socket = s

				case Failure(_) =>
					timeout.trigger()
			}
		}
	}

	/**
	 * Simply forward messages to the Socket object
	 */
	def bound: Receive = {
		// Incoming frame from the WebSocket
		case buffer: Array[Byte] =>
			if (socket != null) socket ! IncomingFrame(buffer)

		// Outgoing frame to the WebSocket
		case OutgoingFrame(frame) =>
			out ! frame
	}

	/**
	 * Called when the Websocket is closed
	 */
	override def postStop() = {
		SocketManager.disconnected(socket)
		WSActor.closed(remote)
	}
}
