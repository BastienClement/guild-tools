package models.application

import models.User
import utils.PubSub

object ApplicationEvents extends PubSub[User] {
	/**
	  * The unread flag for an application was updated
	  */
	case class UnreadUpdated(apply: Int, unread: Boolean)

	/**
	  * The application meta-data was updated
	  */
	case class ApplyUpdated(apply: Application)

	/**
	  * A new message was posted on the application feed
	  */
	case class MessagePosted(message: ApplicationMessage)

	// Increase visibility of publishers
	override protected[application] def publish(msg: Any) = super.publish(msg)
	override protected[application] def !# (msg: Any) = super.publish(msg)
	override protected[application] def publish(msg: Any, filter: (User) => Boolean) = super.publish(msg, filter)
}
