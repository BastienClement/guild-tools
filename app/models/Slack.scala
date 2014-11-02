package models

import java.sql.Timestamp
import models.mysql._

case class Slack(id: Int, user: Int, from: Timestamp, to: Timestamp, reason: Option[String]) {
	lazy val conceal = this.copy(reason = None)
}

/**
 * Absences database
 */
class Slacks(tag: Tag) extends Table[Slack](tag, "gt_slacks") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def user = column[Int]("user")
	def from = column[Timestamp]("from")
	def to = column[Timestamp]("to")
	def reason = column[Option[String]]("reason")

	def * = (id, user, from, to, reason) <> (Slack.tupled, Slack.unapply)
}

/**
 * Helpers
 */
object Slacks extends TableQuery(new Slacks(_)) {
	def notifyCreate(entry: Slack): Unit = {
	}

	def notifyUpdate(entry: Slack): Unit = {
	}

	def notifyDelete(id: Int): Unit = {
	}
}
