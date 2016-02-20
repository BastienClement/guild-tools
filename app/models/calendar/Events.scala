package models.calendar

import java.sql.Timestamp
import models._
import models.mysql._
import reactive.ExecutionContext
import slick.lifted.Case
import utils.PubSub

case class Event(id: Int, title: String, desc: String, owner: Int, date: Timestamp, time: Int, `type`: Int, state: Int) {
	val visibility = `type`

	// Check visibility and state
	if (!EventVisibility.isValid(visibility)) throw new Exception("Invalid event visibility")
	if (!EventState.isValid(state)) throw new Exception("Invalid event state")

	val isRestricted = visibility == EventVisibility.Restricted
	val isAnnounce = visibility == EventVisibility.Announce

	/**
	 * Expand this event to include tabs and slots data
	 */
	lazy val expand =
		for {
			tabs <- Tabs.filter(_.event === this.id).run
			slots <- Slots.filter(_.tab inSet tabs.map(_.id).toSet).run
		} yield {
			val slots_map = slots.groupBy(_.tab.toString).mapValues {
				_.map(s => (s.slot.toString, s)).toMap
			}
			EventFull(this, tabs.toList, slots_map)
		}

	/**
	 * Create a partially visible expanded version of this event
	 */
	lazy val partial = for (expanded <- this.expand) yield {
		// Remove note from hidden tab
		var visibles_tabs = Set[String]()
		val tabs = expanded.tabs map { tab =>
			if (tab.locked) {
				tab.copy(note = None)
			} else {
				visibles_tabs += tab.id.toString
				tab
			}
		}

		// Rebuild slots for visible tabs
		val slots = expanded.slots.filter { case (id, slots) => visibles_tabs.contains(id) }

		EventFull(this, tabs, slots)
	}
}

case class EventFull(event: Event, tabs: List[Tab], slots: Map[String, Map[String, Slot]])

class Events(tag: Tag) extends Table[Event](tag, "gt_events_visible") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def desc = column[String]("desc")
	def owner = column[Int]("owner")
	def date = column[Timestamp]("date")
	def time = column[Int]("time")
	def visibility = column[Int]("type")
	def state = column[Int]("state")
	def garbage = column[Boolean]("garbage")

	def * = (id, title, desc, owner, date, time, visibility, state) <> (Event.tupled, Event.unapply)
}

object Events extends TableQuery(new Events(_)) with PubSub[User] {
	case class Created(event: Event)
	case class Updated(event: Event)
	case class Deleted(event: Event)

	def canAccess(user: User)(row: (Events, Rep[Option[Answers]])): Rep[Boolean] = {
		val (event, answer) = row
		Case
			.If(event.visibility === EventVisibility.Announce).Then(user.fs)
			.If(event.visibility === EventVisibility.Guild).Then(user.fs)
			.If(event.visibility === EventVisibility.Public).Then(true)
			.If(event.visibility === EventVisibility.Roster).Then(user.roster)
			.If(event.visibility === EventVisibility.Restricted).Then(answer.isDefined)
			.Else(false)
	}

	def forUser(user: User) = {
		val ev_answr = this joinLeft Answers.filter(_.user === user.id) on { case (ev, an) => ev.id === an.event }
		ev_answr.withFilter(canAccess(user))
	}

	def byId(id: Rep[Int], user: User) = forUser(user).filter { case (e, a) => e.id === id }

	def between(from: Rep[Timestamp], to: Rep[Timestamp], user: User) = {
		this.forUser(user).filter { case (e, a) => e.date.between(from, to) }
	}
}
