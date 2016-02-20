package channels

import akka.actor.Props
import gtp3._
import models._
import models.calendar.Events
import models.mysql._
import utils.SmartTimestamp
import reactive.ExecutionContext

object CalendarChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.roster) request.accept(Props(new CalendarChannel(request.user)))
		else request.reject(1, "Unauthorized")
	}
}

class CalendarChannel(user: User) extends ChannelHandler {
	message("request-events") { p =>
		"^(201[0-9])\\-(0[1-9]|1[0-2])$".r.findFirstMatchIn(p.string) match {
			case None => throw new IllegalArgumentException(s"Bad date requested: ${p.string}")
			case Some(matched) =>
				val year = matched.group(1).toInt
				val month = matched.group(2).toInt - 1

				val from = SmartTimestamp(year, month, 1)
				val to = SmartTimestamp(year, month + 1, 0)

				for (events <- Events.between(from, to, user).sortBy { case (e, a) => e.date.asc }.run) {
					send("events", events)
				}

				/*for {
					events <- query.sortBy { case (e, a) => e.date.asc }.run
				} {
					send("events", events)
				}*/
		}
	}
}
