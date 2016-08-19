package channels

import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import model.User
import model.calendar.{Event, EventVisibility}
import models._
import models.calendar.{Answers, Events, Slacks}
import models.mysql._
import reactive.ExecutionContext
import util.DateTime
import util.DateTime.Units

object CalendarChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.fs) request.accept(Props(new CalendarChannel(request.user)))
		else request.reject(1, "Unauthorized")
	}
}

class CalendarChannel(user: User) extends ChannelHandler {
	init {
		Events.subscribe(user)
		Answers.subscribe(user)
	}

	akka {
		case Events.Updated(event) => send("event-updated", event)
		case Events.Deleted(event) => send("event-deleted", event)
		case Answers.Updated(answer) => send("answer-updated", answer)
	}

	/**
	  * Requests events and slacks data for a specific month.
	  */
	request("load-month") { key: Int =>
		val month = key % 12
		val year = key / 12 + 2000

		val from = DateTime(year, month + 1, 1)
		val to = DateTime(year, month + 1, 1) + 1.month - 1.day

		val events = Events.findBetween(from, to).filter(Events.canAccess(user))
		val events_answers = Answers.withOwnAnswer(events, user).run
		val slacks = Slacks.findBetween(from, to).run.map(_.map(_.conceal))

		events_answers.zip(slacks)
	}

	/**
	  * Requests answers to a specific event.
	  */
	request("event-answers") { event: Int =>
		Events.ifAccessible(user, event) {
			Answers.findForEvent(event).run
		}
	}

	/**
	  * Changes an event state.
	  */
	message("change-event-state") { (event: Int, state: Int) =>
		Events.ifEditable(user, event) {
			Events.changeState(event, state)
		}
	}

	/**
	  * Changes the user's answer to an event.
	  */
	message("change-event-answer") { (event: Int, answer: Int, toon: Option[Int], note: Option[String]) =>
		Events.ifAccessible(user, event) {
			Answers.changeAnswer(user.id, event, answer, toon, note)
		}
	}

	message("create-event") { (template: Event, dates: Set[DateTime]) =>
		require(dates.nonEmpty)

		if (!EventVisibility.canCreate(template.visibility, user))
			throw new Exception("You don't have the permission to create this kind of event")

		if (!user.promoted && dates.size > 1)
			throw new Exception("You don't have the permission to creates multiple events at once")

		for (date <- dates) {
			Events.create(template.copy(owner = user.id, date = date, state = 0))
		}
	}

	message("delete-event") { id: Int =>
		for {
			event <- Events.findById(id).head
			answer <- Answers.findForEventAndUser(id, user.id).headOption
		} if ((event, answer) match {
			case (ev, _) if ev.owner == user.id || user.promoted => true
			case (_, Some(a)) => a.promote
			case _ => false
		}) {
			Events.delete(id)
		}
	}
}
