package gtp3

import actors.Actors._
import akka.actor.{Actor, ActorRef, PoisonPill}
import gtp3.SocketActor._
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}
import utils.Timeout

object SocketActor {
	// A failed future used when socket binding is obviously failed
	val BindingFailed = Future.failed(null)
}

class SocketActor(val out: ActorRef, val remote: String) extends Actor {
	// The attached socket object
	var socket: ActorRef = null

	// Instantly kill socket if too many are being created from one IP address
	if (!SocketManager.accept(remote)) {
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

			// Timeout to prevent a bug in SocketManager to keep the socket open
			val kill = Timeout(15.seconds) {
				self ! PoisonPill
			}

			context.become(bound)
			kill.start()

			status onComplete {
				case Success(s) =>
					kill.cancel()
					self ! s

				case Failure(_) =>
					kill.trigger()
			}
		}
	}

	/**
	 * Simply forward messages to the Socket object
	 */
	def bound: Receive = {
		case s: ActorRef => socket = s
		case buffer: Array[Byte] => if (socket != null) socket ! buffer
		case frame: Frame => out ! Frame.encode(frame).toByteArray
	}

	/**
	 * Called when the Websocket is closed
	 */
	override def postStop(): Unit = SocketManager.disconnected(self, remote)
}
