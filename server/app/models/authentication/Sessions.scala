package models.authentication

import models.mysql._
import util.DateTime

case class Session(token: String, user: Int, ip: Option[String], ua: Option[String], created: DateTime, last_access: DateTime)

class Sessions(tag: Tag) extends Table[Session](tag, "gt_sessions") {
	def token = column[String]("token", O.PrimaryKey)
	def user = column[Int]("user")
	def ip = column[Option[String]]("ip")
	def ua = column[Option[String]]("ua")
	def created = column[DateTime]("created")
	def last_access = column[DateTime]("last_access")

	def * = (token, user, ip, ua, created, last_access) <> (Session.tupled, Session.unapply)
}

object Sessions extends TableQuery(new Sessions(_)) {
	def findByUser(user: Rep[Int]) = Sessions.filter(_.user === user)
}
