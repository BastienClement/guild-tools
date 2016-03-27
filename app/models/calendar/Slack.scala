package models.calendar

import models.mysql._
import utils.DateTime
import models._

case class Slack(id: Int, user: Int, from: DateTime, to: DateTime, reason: Option[String]) {
	lazy val conceal = this.copy(reason = None)
}

class Slacks(tag: Tag) extends Table[Slack](tag, "gt_slacks") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def user = column[Int]("user")
	def from = column[DateTime]("from")
	def to = column[DateTime]("to")
	def reason = column[Option[String]]("reason")

	def * = (id, user, from, to, reason) <> (Slack.tupled, Slack.unapply)
}

object Slacks extends TableQuery(new Slacks(_)) {
	def findBetween(from: Rep[DateTime], to: Rep[DateTime]) = {
		this.filter(s => s.from >= from && s.to <= to)
	}
}
