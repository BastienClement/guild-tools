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
		if (request.user.roster) request.accept(Props(new CalendarChannel(request.user)))
		else request.reject(1, "Unauthorized")
	}
}

class CalendarChannel(user: User) extends ChannelHandler {
	init {
		Events.subscribe(user)
	}

	akka {
		case Events.Created(event) => send("event-created", event)
		case Events.Updated(event) => send("event-updated", event)
		case Events.Deleted(event) => send("event-deleted", event)
	}

	/**
	  * Requests events and slacks data for a specific month.
	  * The month is given as the "month-key" in client-side application.
	  * (Actually identical to the first part of an ISO date string.
	  */
	message("request-events") { month_key: String =>
		"^(201[0-9])\\-(0[1-9]|1[0-2])$".r.findFirstMatchIn(month_key) match {
			case None => throw new IllegalArgumentException(s"Bad date requested: $month_key")
			case Some(matched) =>
				val year = matched.group(1).toInt
				val month = matched.group(2).toInt

				val from = DateTime(year, month, 1)
				val to = DateTime(year, month, 1) + 1.month - 1.day

				val events = Events.findBetween(from, to).filter(Events.canAccess(user))
				val events_answers = Answers.withOwnAnswer(events, user).run
				val slacks = Slacks.findBetween(from, to).run.map(_.map(_.conceal))

				for ((ea, s) <- events_answers.zip(slacks)) {
					send("events", (ea, s, month_key))
				}
		}
	}

	/**
	  * Requests answers to a specific event.
	  */
	request("event-answers") { event: Int =>
		Events.ifAccessible(user, event) {
			Answers.findForEvent(event).run.await
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
	message("change-event-answer") { (event: Int, answer: Int, toon: Option[Int], note: String) =>
	}
}
