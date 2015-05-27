package gtp3

import gt.Global.ExecutionContext

import scala.util.{Failure, Success}

class Channel(val socket: Socket, val id: Int, val sender_channel: Int, val handler: ChannelHandler) {
	def receive(frame: ChannelFrame) = frame match {
		case RequestFrame(seq, channel, req, rid, flags, payload) =>
			handler.request(req, Payload(payload, flags)) onComplete {
				case Success(res_payload) =>
					socket.out ! SuccessFrame(0, sender_channel, rid, res_payload.flags, res_payload.byteVector)

				case Failure(fail) =>
					socket.out ! FailureFrame(0, sender_channel, rid, 0, fail.getMessage)
			}
	}

	def close(code: Int, reason: String) = {
		println("Channel closed", code, reason)
		socket.channelClosed(this)
	}
}
