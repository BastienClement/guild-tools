package models.application

import java.sql.Timestamp
import models.User
import models.application.ApplicationEvents.UnreadUpdated
import models.mysql._
import reactive.ExecutionContext
import utils.SmartTimestamp

case class ApplicationReadState(user: Int, apply: Int, date: Timestamp)

object ApplicationReadStates extends TableQuery(new ApplicationReadStates(_)) {
	/**
	  * Compute the correct unread state flag for an application and an user
	  */
	val isUnread = Compiled((application: Rep[Int], user: Rep[Int], member: Rep[Boolean]) => {
		val default_read = SmartTimestamp(2000, 1, 1).toSQL
		val last_msg = ApplicationFeed.forApplication.extract(application, member).map(_.date).max.ifNull(default_read)
		val last_read = ApplicationReadStates.filter(r => r.apply === application && r.user === user).map(_.date).max.ifNull(default_read)
		last_read < last_msg
	})

	/**
	  * Update the read state flag
	  */
	def markAsRead(apply_id: Int, user: User) = for {
		r <- ApplicationReadStates insertOrUpdate ApplicationReadState(user.id, apply_id, SmartTimestamp.now)
		_ = ApplicationEvents.publish(UnreadUpdated(apply_id, false), u => u.id == user.id)
	} yield r
}

class ApplicationReadStates(tag: Tag) extends Table[ApplicationReadState](tag, "gt_apply_read") {
	def user = column[Int]("user", O.PrimaryKey)
	def apply = column[Int]("apply", O.PrimaryKey)
	def date = column[Timestamp]("date")

	def * = (user, apply, date) <> (ApplicationReadState.tupled, ApplicationReadState.unapply)
}
