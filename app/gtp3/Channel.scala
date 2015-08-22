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
					//println(fail.printStackTrace())
					socket.out ! FailureFrame(0, sender_channel, rid, 0, fail.getMessage)
			}

		case CloseFrame(seq, channel, code, reason) =>
			this.closed()
	}

	def send(msg: String, payload: Payload) = {
		socket.out ! MessageFrame(0, sender_channel, msg, payload.flags, payload.byteVector)
	}

	/*def close(code: Int, reason: String) = {

	}*/

	def closed() = {
		handler match {
			case c: CloseHandler => c.close()
			case _ => /* noop */
		}
		socket.channelClosed(this)
	}
}
