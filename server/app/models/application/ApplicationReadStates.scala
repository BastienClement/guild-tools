package models.application

import models._
import models.application.ApplicationEvents.UnreadUpdated
import models.mysql._
import reactive.ExecutionContext
import utils.DateTime

case class ApplicationReadState(user: Int, apply: Int, date: DateTime)

object ApplicationReadStates extends TableQuery(new ApplicationReadStates(_)) {
	/**
	  * Compute the correct unread state flag for an application and an user
	  */
	val isUnread = Compiled((application: Rep[Int], user: Rep[Int], member: Rep[Boolean]) => {
		val default_read = DateTime(2000, 1, 1)
		val last_msg = ApplicationFeed.forApplication.extract(application, member).map(_.date).max.ifNull(default_read)
		val last_read = ApplicationReadStates.filter(r => r.apply === application && r.user === user).map(_.date).max.ifNull(default_read)
		last_read < last_msg
	})

	/**
	  * Update the read state flag
	  */
	def markAsRead(apply_id: Int, user: User) = for {
		r <- ApplicationReadStates insertOrUpdate ApplicationReadState(user.id, apply_id, DateTime.now)
		_ = ApplicationEvents.publish(UnreadUpdated(apply_id, false), u => u.id == user.id)
	} yield r
}

class ApplicationReadStates(tag: Tag) extends Table[ApplicationReadState](tag, "gt_apply_read") {
	def user = column[Int]("user", O.PrimaryKey)
	def apply = column[Int]("apply", O.PrimaryKey)
	def date = column[DateTime]("date")

	def * = (user, apply, date) <> (ApplicationReadState.tupled, ApplicationReadState.unapply)
}
