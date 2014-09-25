package api

import play.api.libs.json.JsValue

case class Message(cmd: String, arg: JsValue)

abstract class MessageResponse()
case class MessageSuccess() extends MessageResponse()
case class MessageResults(res: JsValue) extends MessageResponse()
case class MessageFailed(err: String, message: String = "") extends MessageResponse
