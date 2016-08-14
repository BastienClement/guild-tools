package model.calendar

import util.DateTime
import util.annotation.data

@data case class Event(id: Int, title: String, desc: String, owner: Int,
                       date: DateTime, time: Int, `type`: Int, state: Int) {
	val visibility = `type`

	val isRestricted = visibility == EventVisibility.Restricted
	val isAnnounce = visibility == EventVisibility.Announce

	val sortingTime = if (time < 600) time + 2400 else time
}
