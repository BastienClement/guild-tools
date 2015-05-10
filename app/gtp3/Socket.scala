package gtp3

import scala.util.Success
import akka.actor.{ActorPath, ActorRef}
import gt.Global
import scodec.Attempt.Successful
import scodec.DecodeResult
import scodec.bits.BitVector

case class OutProxy(sock: Socket) {
	def !(message: BitVector) = {
		if (sock.isOpen) sock.actor.out ! message.toByteArray
	}
}

class Socket(val id: Long, var actor: SocketActor) {
	// Socket state
	private var open = true
	def isOpen = open

	// Safe proxy to actor.out
	object out {
		def !(message: BitVector) = {
			if (isOpen) actor.out ! message.toByteArray
		}
	}

	out ! Frame.encode(HandshakeFrame(GTP3Magic, Global.serverVersion, id))

	def receive(buffer: Array[Byte]) = {
		println(Frame.decode(buffer))
	}

	def rebind(a: SocketActor, last_seq: Int) = {
		open = true
		actor = a
	}

	def detach() = {
		open = false
		actor = null
	}
}
