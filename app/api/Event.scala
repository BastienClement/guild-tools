package api

abstract class Event(val name: String)
abstract class FilterableEvent(n: String) extends Event(n)

case class EventCreate() extends FilterableEvent("EventCreate")
case class EventUpdate() extends FilterableEvent("EventUpdate")
case class EventDelete() extends FilterableEvent("EventDelete")
