package models.calendar

import utils.annotation.data

@data case class EventFull(event: Event, tabs: List[Tab], slots: Map[String, Map[String, Slot]])
