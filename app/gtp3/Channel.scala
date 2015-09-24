package gtp3

import gt.Global.ExecutionContext
import org.apache.commons.lang3.exception.ExceptionUtils

import scala.util.{Failure, Success}

class Channel(val socket: Socket, val id: Int, val sender_channel: Int, val handler: ChannelHandler) {
	def receive(frame: ChannelFrame) = frame match {
		case MessageFrame(seq, channel, message, flags, payload) =>
			handler.message(message, Payload(payload, flags))

		case RequestFrame(seq, channel, req, rid, flags, payload) =>
			handler.request(req, Payload(payload, flags)) onComplete {
				case Success(res_payload) =>
					socket.out ! SuccessFrame(0, sender_channel, rid, res_payload.flags, res_payload.byteVector)

				case Failure(fail) =>
					//println(fail.printStackTrace())
					socket.out ! FailureFrame(0, sender_channel, rid, 0, ExceptionUtils.getStackTrace(fail))
			}

		case SuccessFrame(seq, channel, req, flags, payload) => ???
		case FailureFrame(seq, channel, req, code, message) => ???

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
