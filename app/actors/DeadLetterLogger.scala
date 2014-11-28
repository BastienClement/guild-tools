package actors

import akka.actor.{Actor, DeadLetter}
import play.api.Logger

class DeadLetterLogger extends Actor {
	def receive = {
		case DeadLetter(msg, from, to) =>
			Logger.warn(s"DeadLetter from $from to $to -> $msg")
	}
}
