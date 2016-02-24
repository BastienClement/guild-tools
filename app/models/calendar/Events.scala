package models.calendar

import java.sql.Timestamp
import models._
import models.mysql._
import reactive.ExecutionContext
import scala.concurrent.Future
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

	def * = (id, title, desc, owner, date, time, visibility, state) <>(Event.tupled, Event.unapply)
}

object Events extends TableQuery(new Events(_)) with PubSub[User] {
	case class Created(event: Event)
	case class Updated(event: Event)
	case class Deleted(event: Event)

	/**
	  * Checks if a user can access an event.
	  * Pure scala version.
	  *
	  * @param user   The user to test
	  * @param event  The event
	  * @param answer The answer of this user for this event
	  * @return Whether the user is allowed to access the event
	  */
	def canAccess(user: User, event: Event, answer: Option[Answer] = None): Boolean = {
		event.visibility match {
			case EventVisibility.Announce => user.fs
			case EventVisibility.Guild => user.fs
			case EventVisibility.Public => true
			case EventVisibility.Roster => user.roster
			case EventVisibility.Restricted => answer.isDefined
			case _ => false
		}
	}

	/**
	  * Checks if a user can access an event.
	  * SQL version. Can be used as a .filter()
	  *
	  * @param user  The user to test
	  * @param event The event
	  * @return Whether the user is allowed to access the event
	  */
	def canAccess(user: User)(event: Events): Rep[Boolean] = {
		val answer = Answers.findForEventAndUser(event.id, user.id).exists
		Case
			.If(event.visibility === EventVisibility.Announce).Then(user.fs)
			.If(event.visibility === EventVisibility.Guild).Then(user.fs)
			.If(event.visibility === EventVisibility.Public).Then(true)
			.If(event.visibility === EventVisibility.Roster).Then(user.roster)
			.If(event.visibility === EventVisibility.Restricted).Then(answer)
			.Else(false)
	}

	/**
	  * Checks if a user can access an event.
	  * This version takes an event id instead of an event object.
	  *
	  * @param user The user to test
	  * @param id   The event id
	  * @return Whether the user is allowed to access the event
	  */
	def canAccess(user: User, id: Int): Rep[Boolean] = {
		Events.findById(id).filter(canAccess(user)).exists
	}

	/**
	  * Checks if a user is allowed to edit an event.
	  * A user is allowed to edit an event if they are the event owner or if they are promoted,
	  * either globally (devs, officers) or specifically for this event.
	  *
	  * @param user  The user to test
	  * @param event The event
	  * @return Whether the user is allowed to edit the event
	  */
	def canEdit(user: User)(event: Events): Rep[Boolean] = {
		val promoted = Answers.findForEventAndUser(event.id, user.id).filter(_.promote).exists
		event.owner === user.id || user.promoted || promoted
	}

	/**
	  * Runs an action if the given event is accessible by the user.
	  *
	  * @param user The user to test
	  * @param id   The event id
	  */
	def ifAccessible[T](user: User, id: Int)(action: => T): Future[T] = {
		for (_ <- Events.findById(id).filter(canAccess(user)).head) yield action
	}

	/**
	  * Runs an action if the given event is editable by the user.
	  *
	  * @param user The user to test
	  * @param id   The event's ID
	  */
	def ifEditable[T](user: User, id: Int)(action: => T): Future[T] = {
		for (_ <- Events.findById(id).filter(canEdit(user)).head) yield action
	}

	/**
	  * Finds an event by ID.
	  *
	  * @param id The event's ID
	  * @return A Query for this event
	  */
	def findById(id: Rep[Int]) = {
		Events.filter(_.id === id)
	}

	/**
	  * Finds events between two dates.
	  *
	  * @param from The lower-bound date
	  * @param to   The upper-bound date
	  * @return A Query for events between the two dates
	  */
	def findBetween(from: Rep[Timestamp], to: Rep[Timestamp]) = {
		Events.filter(_.date.between(from, to))
	}

	/**
	  * Changes the state (Open, Close, Canceled) of an event.
	  *
	  * @param event_id The event id
	  * @param state    The new event state
	  */
	def changeState(event_id: Int, state: Int) = {
		require(EventState.isValid(state))
		for (n <- Events.findById(event_id).filter(_.state =!= state).map(_.state).update(state).run if n > 0) {
			publishUpdate(event_id)
		}
	}

	/**
	  * Publishes an updated message to PubSub subscribers.
	  *
	  * @param event_id The calendar event ID
	  * @param message  An optional constructor for the message object.
	  *                 Defaults to Events.Updated.apply
	  */
	private def publishUpdate(event_id: Int, message: (Event) => Any = Updated.apply _) = {
		val queries = for {
			e <- Events.findById(event_id).result.head
			a <- Answers.findForEvent(event_id).result
		} yield (e, a)

		for ((event, raw_answers) <- queries.run) {
			val answers = raw_answers.map(a => (a.user, a)).toMap
			val dispatch_message = message(event)
			this.publish(dispatch_message, u => canAccess(u, event, answers.get(u.id)))
		}
	}
}
