package gtp3

import scala.util.Success
import akka.actor.{ActorPath, ActorRef}
import gt.Global
import scodec.Attempt.Successful
import scodec.DecodeResult
import scodec.bits.BitVector

class Socket(val id: Long, var actor: SocketActor) {
	// Socket state
	private var open = true

	// Safe proxy to actor.out
	object out {
		def !(frame: Frame) = {
			if (open) actor.out ! Frame.encode(frame).toByteArray
		}
	}

	out ! HandshakeFrame(GTP3Magic, Global.serverVersion, id)

	def receive(buffer: Array[Byte]) = Frame.decode(buffer) match {
		case CommandFrame(c) => command(c)
	}

	def command(code: Int) = code match {
		case CommandCode.PING => out ! CommandFrame(CommandCode.PONG)
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
