package api

import play.api.libs.json._
import play.api.libs.json.Json.JsValueWrapper
import models._

abstract class Event(val name: String, val arg: JsValueWrapper) {
	def asJson: JsValue = Json.obj("name" -> name, "arg" -> arg)
}

case class CharCreateEvent(char: Char) extends Event("char:create", char)
case class CharUpdateEvent(char: Char) extends Event("char:update", char)
case class CharDeleteEvent(id: Int) extends Event("char:delete", id)
