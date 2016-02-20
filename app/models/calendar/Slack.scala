package models.calendar

import java.sql.Timestamp
import models.mysql._
import utils.SmartTimestamp

case class Slack(id: Int, user: Int, from: Timestamp, to: Timestamp, reason: Option[String]) {
	lazy val conceal = this.copy(reason = None)
}

class Slacks(tag: Tag) extends Table[Slack](tag, "gt_slacks") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def user = column[Int]("user")
	def from = column[Timestamp]("from")
	def to = column[Timestamp]("to")
	def reason = column[Option[String]]("reason")

	def * = (id, user, from, to, reason) <> (Slack.tupled, Slack.unapply)
}

object Slacks extends TableQuery(new Slacks(_)) {
	def between(from: SmartTimestamp, to: SmartTimestamp) = {
		this.filter(s => s.from >= from.toSQL && s.to <= to.toSQL)
	}
}
