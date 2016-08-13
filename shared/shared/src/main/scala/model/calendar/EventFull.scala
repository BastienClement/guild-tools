package model.calendar

import util.annotation.data

@data case class EventFull(event: Event, tabs: List[Tab], slots: Map[String, Map[String, Slot]])
