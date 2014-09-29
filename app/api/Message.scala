package api

import play.api.libs.json.{ JsString, JsValue }

trait OutgoingMessage
case class Message(cmd: String, arg: JsValue) extends OutgoingMessage
case class CloseMessage(arg: String) extends OutgoingMessage

trait MessageResponse
case class MessageSuccess() extends MessageResponse
case class MessageResults(res: JsValue) extends MessageResponse
case class MessageFailure(err: String, message: String = "") extends MessageResponse
case class MessageSilent() extends MessageResponse
