package model.calendar

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
