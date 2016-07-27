package models.application

import model.User
import models.application.ApplicationEvents.{ApplyUpdated, MessagePosted, UnreadUpdated}
import models.mysql._
import reactive.ExecutionContext
import utils.DateTime

case class ApplicationMessage(id: Int, apply: Int, user: Int, date: DateTime, text: String, secret: Boolean, system: Boolean)

object ApplicationFeed extends TableQuery(new ApplicationFeed(_)) {
	/**
	  * Fetch application feed for an application
	  */
	val forApplication = Compiled((application: Rep[Int], with_secret: Rep[Boolean]) => {
		for (msg <- ApplicationFeed if msg.apply === application && (with_secret || !msg.secret)) yield msg
	})

	/**
	  * Fetch application feed for an application. Return sorted results
	  */
	val forApplicationSorted = Compiled((application: Rep[Int], with_secret: Rep[Boolean]) => {
		forApplication.extract(application, with_secret).sortBy(_.date.asc)
	})

	/**
	  * Post a new message in an application
	  */
	def postMessage(sender: User, apply_id: Int, message: String, secret: Boolean = true, system: Boolean = false) = {
		// Attempt to send a private message, but the user is not a member
		if (secret && !sender.member)
			throw new Exception("You are not allowed to post private messages on applications")

		val now = DateTime.now
		val msg = ApplicationMessage(0, apply_id, sender.id, now, message, secret, system)

		/**
		  * - Fetch the application and ensure that the user can access it
		  * - Insert the message in the database
		  * - Set have_posts and updated field for the application
		  * - Adjust application object fields to match what we have just changed
		  */
		val action = for {
			application_opt <- Applications.byIdChecked(apply_id, sender.id, sender.member, sender.promoted).result.headOption
			application = application_opt.getOrElse(throw new Exception("You are not allowed to access this application"))
			msg_id <- (ApplicationFeed returning ApplicationFeed.map(_.id)) += msg
			_ <- Applications.filter(_.id === apply_id).map(a => (a.have_posts, a.updated)).update((true, now))
			application_final = application.copy(have_posts = true, updated = now)
		} yield (application_final, msg_id)

		// Execution action and then send events
		for {
			(apply, msg_id) <- action.transactionally
			_ = {
				// Only send event to users that can access application and are members if the message is secret
				def filter(user: User) = Applications.canAccess(user, apply) && (!secret || user.member)
				val msg_final = msg.copy(id = msg_id)
				ApplicationEvents.publish(ApplyUpdated(apply), filter)
				ApplicationEvents.publish(MessagePosted(msg_final), filter)
				ApplicationEvents.publish(UnreadUpdated(apply.id, true), filter)
			}
		} yield (apply, msg_id)
	}
}

class ApplicationFeed(tag: Tag) extends Table[ApplicationMessage](tag, "gt_apply_feed") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def apply = column[Int]("apply")
	def user = column[Int]("user")
	def date = column[DateTime]("date")
	def text = column[String]("text")
	def secret = column[Boolean]("secret")
	def system = column[Boolean]("system")

	def * = (id, apply, user, date, text, secret, system) <> (ApplicationMessage.tupled, ApplicationMessage.unapply)
}
