package api

import actors.Dispatchable
import models._
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import utils.AllowedByDefault

/**
 * Base class for internal-only events
 */
trait InternalEvent extends Dispatchable

/**
 * Base class for generics client-side sendable events
 */
abstract class Event(val name: String, val arg: JsValueWrapper) extends Dispatchable {
	def asJson: JsValue = Json.obj("name" -> name, "arg" -> arg)
}

/**
 * Base class for core.js-managed events
 */
abstract class CoreEvent(name: String, arg: JsValueWrapper) extends Event(name, arg) with AllowedByDefault

/**
 * Chat event
 */
case class ChatUserConnect(user: Int) extends CoreEvent("chat:user:connect", user)
case class ChatUserDisconnect(user: Int) extends CoreEvent("chat:user:disconnect", user)
case class ChatShoutboxMsg(msg: ChatMessage) extends CoreEvent("chat:shoutbox:msg", msg)

/**
 * RosterEvent
 */
case class RosterCharUpdate(char: Char) extends CoreEvent("roster:char:update", char)
case class RosterCharDelete(char: Int) extends CoreEvent("roster:char:delete", char)

/**
 * Context-specific events
 */
abstract class CtxEvent(name: String, arg: JsValueWrapper) extends Event(name, arg)

/**
 * Dashboard events
 */
case class DashboardFeedUpdate(data: List[Feed]) extends CtxEvent("dashboard:feed:update", data)

/**
 * Calendar events
 */
case class CalendarEventCreate(event: CalendarEvent) extends CtxEvent("calendar:event:create", event)
case class CalendarEventUpdate(event: CalendarEvent) extends CtxEvent("calendar:event:update", event)
case class CalendarEventUpdateFull(full: CalendarEventFull) extends CtxEvent("calendar:event:update:full", full)
case class CalendarEventDelete(id: Int) extends CtxEvent("calendar:event:delete", id)

/**
 * Calendar answers
 */
case class CalendarAnswerCreate(answer: CalendarAnswer) extends CtxEvent("calendar:answer:create", answer)
case class CalendarAnswerUpdate(answer: CalendarAnswer) extends CtxEvent("calendar:answer:update", answer)
case class CalendarAnswerDelete(user: Int, event: Int) extends CtxEvent("calendar:answer:delete", Json.obj("user" -> user, "event" -> event))

/**
 * Calendar tabs manipulations
 */
case class CalendarTabCreate(tab: CalendarTab) extends CtxEvent("calendar:tab:create", tab)
case class CalendarTabUpdate(tab: CalendarTab) extends CtxEvent("calendar:tab:update", tab)
case class CalendarTabDelete(id: Int) extends CtxEvent("calendar:tab:delete", id)
case class CalendarTabWipe(id: Int) extends CtxEvent("calendar:tab:wipe", id)

/**
 * Calendar locks
 */
case class CalendarLockAcquire(id: Int, owner: String) extends CtxEvent("calendar:lock:acquire", Json.obj("id" -> id, "owner" -> owner))
case class CalendarLockRelease(id: Int) extends CtxEvent("calendar:lock:release", id)

/**
 * Calendar raid-comp
 */
case class CalendarSlotUpdate(slot: CalendarSlot) extends CtxEvent("calendar:slot:update", slot)
case class CalendarSlotDelete(tab: Int, slot: Int) extends CtxEvent("calendar:slot:delete", Json.obj("tab" -> tab, "slot" -> slot))

/**
 * Absences
 */
case class SlackCreate(slack: Slack) extends CtxEvent("absence:create", slack)
case class SlackUpdate(slack: Slack) extends CtxEvent("absence:update", slack)
case class SlackDelete(id: Int) extends CtxEvent("absence:delete", id)

/**
 * Composer
 */
case class ComposerLockoutCreate(lockout: ComposerLockout) extends CtxEvent("composer:lockout:create", lockout)
case class ComposerLockoutDelete(id: Int) extends CtxEvent("composer:lockout:delete", id)
case class ComposerGroupCreate(group: ComposerGroup) extends CtxEvent("composer:group:create", group)
case class ComposerGroupDelete(id: Int) extends CtxEvent("composer:group:delete", id)
case class ComposerSlotSet(slot: ComposerSlot) extends CtxEvent("composer:slot:set", slot)
case class ComposerSlotUnset(group: Int, char: Int) extends CtxEvent("composer:slot:unset", Json.obj("group" -> group, "char" -> char))

//case class ComposerCreate(id: Int) extends CtxEvent("absence:delete", id)
