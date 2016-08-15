package channels

import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import model.User
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
		case Events.Created(event) => send("event-updated", event)
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
}
