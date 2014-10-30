package api

import scala.concurrent.Future
import play.api.libs.json.Json.JsValueWrapper

trait OutgoingMessage
case class Message(cmd: String, arg: JsValueWrapper) extends OutgoingMessage
case class CloseMessage(arg: String) extends OutgoingMessage

trait MessageResponse
case object MessageSuccess extends MessageResponse
case class MessageResults(res: JsValueWrapper) extends MessageResponse
case class MessageFailure(err: String, message: String = "") extends MessageResponse
case class MessageAlert(alert: String) extends MessageResponse
case class MessageDeferred(f: Future[MessageResponse]) extends MessageResponse
