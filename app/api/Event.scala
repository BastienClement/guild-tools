package api

abstract class Event(val name: String)
abstract class ContextualEvent(n: String) extends Event(n)

case class ProfileCharCreate() extends ContextualEvent("ProfileCharCreate")
case class ProfileCharUpdate() extends ContextualEvent("ProfileCharUpdate")
case class ProfileCharDelete() extends ContextualEvent("ProfileCharDelete")
case class ProfileMainChange() extends ContextualEvent("ProfileMainChange")

case class EventCreate() extends ContextualEvent("EventCreate")
case class EventUpdate() extends ContextualEvent("EventUpdate")
case class EventDelete() extends ContextualEvent("EventDelete")