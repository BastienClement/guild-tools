package gtp3

import akka.actor.{Actor, ActorRef, PoisonPill}
import gt.Global.ExecutionContext
import gtp3.SocketActor._
import play.api.mvc.RequestHeader
import scodec.Codec
import scodec.bits.BitVector
import scodec.codecs._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object SocketActor {
	// A failed future used when socket binding is obviously failed
	val BindingFailed = Future.failed(null)
}

class SocketActor(val out: ActorRef, val request: RequestHeader) extends Actor {
	// The attached socket object
	var socket: Socket = null

	// Special-case frames
	private case class HelloFrame(magic: Int)
	private case class ResumeFrame(sockid: Long, seq: Int)

	// Codec for these frames
	private implicit val HelloFrameCodec = (constant(Opcodes.HELLO) :~>: int32).as[HelloFrame]
	private implicit val ResumeFrameCodec = (constant(Opcodes.RESUME) :~>: int64 :: uint16).as[ResumeFrame]

	// Instantly kill socket if too many are being created from one IP address
	if (!SocketManager.validate(this)) {
		self ! PoisonPill
	}

	/**
	 * Handshake reception
	 */
	def receive = {
		case buffer: Array[Byte] => {
			// Parse the handshake frame
			val data = BitVector(buffer)
			val frame = Codec.decode[HelloFrame](data) orElse Codec.decode[ResumeFrame](data)

			val status = frame fold(_ => BindingFailed, _.value match {
				// Create a new socket for this client
				case HelloFrame(magic) =>
					if (magic == ProtocolMagic) SocketManager.allocate(this)
					else BindingFailed

				// Rebind an existing socket
				case ResumeFrame(sockid, seq) => SocketManager.rebind(this, sockid, seq)

				// Bad stuff
				case _ => BindingFailed
			})

			context.become(pending)

			status onComplete {
				case Success(s) =>
					socket = s
					context.become(bound)

				case Failure(_) =>
					self ! PoisonPill
			}
		}
	}

	/**
	 * The actor is placed in this state while waiting for SocketManager
	 * to create or rebind a socket
	 */
	def pending: Receive = {
		case _ => /* discard */
	}

	/**
	 * Simply forward messages to the Socket object
	 */
	def bound: Receive = {
		case buffer: Array[Byte] => socket.receive(buffer)
	}

	/**
	 * Called when the Websocket is closed
	 */
	override def postStop(): Unit = {
		if (socket != null) socket.detach()
		SocketManager.closed(this)
	}
}
