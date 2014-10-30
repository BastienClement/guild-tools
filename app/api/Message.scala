package api

import scala.concurrent.Future
import gt.Global.ExecutionContext
import play.api.libs.json.Json.JsValueWrapper
import scala.language.implicitConversions

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

abstract class MessageResponse
case object MessageSuccess extends MessageResponse
case class MessageResults(res: JsValueWrapper) extends MessageResponse
case class MessageFailure(err: String, message: String = "") extends MessageResponse
case class MessageAlert(alert: String) extends MessageResponse
case class MessageDeferred(f: Future[MessageResponse]) extends MessageResponse
