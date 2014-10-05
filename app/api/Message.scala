package api

import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper

trait OutgoingMessage
case class Message(cmd: String, arg: JsValueWrapper) extends OutgoingMessage
case class CloseMessage(arg: String) extends OutgoingMessage

case class Event(name: String, arg: JsValueWrapper)

trait MessageResponse
case class MessageSuccess() extends MessageResponse
case class MessageResults(res: JsValueWrapper) extends MessageResponse
case class MessageFailure(err: String, message: String = "") extends MessageResponse
