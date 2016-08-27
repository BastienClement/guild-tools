package models.calendar

import boopickle.DefaultBasic._
import utils.DateTime
import utils.annotation.data

@data case class Event(id: Int, title: String, desc: String, owner: Int,
                       date: DateTime, time: Int, `type`: Int, state: Int) {
	require(EventVisibility.isValid(`type`), s"invalid event visibility (${`type`})")
	require(EventState.isValid(state), s"invalid event state ($state)")
	require(0 <= time && time < 2400, s"invalid event time ($time)")

	@inline final def visibility = `type`

	@inline final def isRestricted = visibility == EventVisibility.Restricted
	@inline final def isAnnounce = visibility == EventVisibility.Announce

	@inline final def sortingTime = if (time < 600) time + 2400 else time
}

object Event {
	implicit val EventPickler = PicklerGenerator.generatePickler[Event]

	implicit val EventOrdering = new Ordering[Event] {
		def compare(x: Event, y: Event): Int = {
			if (x.date != y.date) x.date compare y.date
			else if (x.isAnnounce != y.isAnnounce) y.isAnnounce compare x.isAnnounce
			else if (x.sortingTime != y.sortingTime) x.sortingTime compare y.sortingTime
			else x.id compare y.id
		}
	}
}
