package api

import models._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._

abstract class Event(val name: String, val arg: JsValueWrapper) {
	def asJson: JsValue = Json.obj("name" -> name, "arg" -> arg)
}

case class CharCreate(char: Char) extends Event("char:create", char)
case class CharUpdate(char: Char) extends Event("char:update", char)
case class CharDelete(id: Int) extends Event("char:delete", id)

case class CalendarEventCreate(event: CalendarEvent) extends Event("event:create", event)
case class CalendarEventUpdate(event: CalendarEvent) extends Event("event:update", event)
case class CalendarEventDelete(id: Int) extends Event("event:delete", id)

case class CalendarAnswerCreate(answer: CalendarAnswer) extends Event("answer:create", answer)
case class CalendarAnswerUpdate(answer: CalendarAnswer) extends Event("answer:update", answer)
case class CalendarAnswerDelete(id: Int) extends Event("answer:delete", id)
