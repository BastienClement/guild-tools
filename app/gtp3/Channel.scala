package gtp3

import akka.actor._
import akka.pattern.ask
import gt.Global.ExecutionContext
import gtp3.Channel._
import gtp3.ChannelHandler.SendMessage
import org.apache.commons.lang3.exception.ExceptionUtils

object Channel {
	case class Init(channel: ActorRef)
	case class Close(code: Int = 0, reason: String = "Channel closed")
	case class Message(message: String, payload: Payload)
	case class Request(rid: Int, request: String, payload: Payload)
	case class Success(rid: Int, payload: Payload)
	case class Failure(rid: Int, fail: Throwable)

	def props(socket: ActorRef, id: Int, sender_channel: Int, handler: Props) =
		Props(new Channel(socket, id, sender_channel, handler))
}

class Channel(val socket: ActorRef, val id: Int, val sender_channel: Int, val handler_props: Props) extends Actor {
	// Create channel handler
	val handler = context.actorOf(handler_props, "handler")
	context.watch(handler)

	// Initialize handler
	handler ! Init(self)

	def receive = {
		case MessageFrame(seq, channel, message, flags, payload) =>
			handler ! Message(message, Payload(payload, flags))

		case RequestFrame(seq, channel, req, rid, flags, payload) =>
			handler ! Request(rid, req, Payload(payload, flags))

		case SuccessFrame(seq, channel, req, flags, payload) => ???
		case FailureFrame(seq, channel, req, code, message) => ???

		case CloseFrame(seq, channel, code, reason) =>
			handler ! Close(code, reason)

		case Success(rid, payload) =>
			socket ! SuccessFrame(0, sender_channel, rid, payload.flags, payload.byteVector)

		case Failure(rid, fail) =>
			socket ! FailureFrame(0, sender_channel, rid, 0, fail.getMessage)

		case SendMessage(msg, payload) =>
			socket ! MessageFrame(0, sender_channel, msg, payload.flags, payload.byteVector)

		case Close(code, reason) =>
			socket ! CloseFrame(0, sender_channel, code, reason)
			handler ! Close(code, reason)

		case Terminated(actor) if actor == handler =>
			self ! PoisonPill
	}
}
