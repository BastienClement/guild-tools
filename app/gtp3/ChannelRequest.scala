package gtp3

import akka.actor.{ActorRef, Props}
import models.User

abstract class ChannelRequest(val socket: ActorRef, val channel_type: String, val token: String, val user: User) {
	protected var _channel: Option[Channel] = None
	def channel = _channel

	protected var _replied = false
	def replied = _replied

	def accept(handler: Props): Unit
	def reject(code: Int, message: String): Unit
}
