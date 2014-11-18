package api

import scala.concurrent.Future
import scala.language.implicitConversions
import gt.Global.ExecutionContext
import play.api.libs.json.Json.JsValueWrapper

/**
 * Server-initiated outgoing message
 */
abstract class OutgoingMessage
case class Message(cmd: String, arg: JsValueWrapper) extends OutgoingMessage
case class CloseMessage(arg: String) extends OutgoingMessage

object MessageResponse {
	implicit def fromFuture(f: Future[MessageResponse]): MessageResponse = MessageDeferred(f)
	implicit def fromFuture[T <% JsValueWrapper](f: Future[T]): MessageResponse = {
		MessageDeferred(f map (MessageResults(_)))
	}
	implicit def fromJsValue[T <% JsValueWrapper](v: T): MessageResponse = MessageResults(v)
}

/**
 * Response to client request
 */
abstract class MessageResponse
case object MessageSuccess extends MessageResponse
case class MessageResults(res: JsValueWrapper) extends MessageResponse
case class MessageFailure(err: String = "") extends MessageResponse
case class MessageDeferred(f: Future[MessageResponse]) extends MessageResponse
