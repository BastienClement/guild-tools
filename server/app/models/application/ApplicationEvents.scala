package models.application

import model.User
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
}
