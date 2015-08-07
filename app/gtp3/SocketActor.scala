package gtp3

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorRef, PoisonPill}
import actors.Actors._
import gt.Global.ExecutionContext
import gtp3.SocketActor._
import utils.Timeout

object SocketActor {
	// A failed future used when socket binding is obviously failed
	val BindingFailed = Future.failed(null)
}

class SocketActor(val out: ActorRef, val remote: String) extends Actor {
	// The attached socket object
	var socket: Socket = null

	// Instantly kill socket if too many are being created from one IP address
	if (!SocketManager.accept(this)) {
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
					if (magic == GTP3Magic) SocketManager.allocate(this)
					else BindingFailed

				// Rebind an existing socket
				case ResumeFrame(sockid, seq) => SocketManager.rebind(this, sockid, seq)

				// Bad stuff
				case _ => BindingFailed
			}

			// Timeout to prevent a bug in SocketManager to keep the socket open
			val kill = Timeout(15.seconds) {
				self ! PoisonPill
			}

			val ctx = context
			ctx.become(bound)
			kill.start()

			status onComplete {
				case Success(s) =>
					kill.cancel()
					socket = s

				case Failure(_) =>
					kill.trigger()
			}
		}
	}

	/**
	 * Simply forward messages to the Socket object
	 */
	def bound: Receive = {
		case buffer: Array[Byte] =>
			if (socket != null) socket.receive(buffer)
	}

	/**
	 * Called when the Websocket is closed
	 */
	override def postStop(): Unit = {
		if (socket != null) socket.detach()
		SocketManager.disconnected(this)
	}
}
