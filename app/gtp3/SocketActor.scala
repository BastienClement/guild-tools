package gtp3

import actors.Actors._
import akka.actor.{Actor, ActorRef}
import play.api.mvc.RequestHeader

class SocketActor(val output: ActorRef, val request: RequestHeader) extends Actor  {
	var socket: Socket = null
	def receive = {
		case buffer: Array[Byte] =>
	}

	/**
	 * Websocket is now closed
	 */
	override def postStop(): Unit = {

	}
}
