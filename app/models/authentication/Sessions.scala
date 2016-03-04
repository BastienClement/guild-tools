package models.authentication

import java.sql.Timestamp
import models.mysql._

case class Session(token: String, user: Int, ip: Option[String], ua: Option[String], created: Timestamp, last_access: Timestamp)

class Sessions(tag: Tag) extends Table[Session](tag, "gt_sessions") {
	def token = column[String]("token", O.PrimaryKey)
	def user = column[Int]("user")
	def ip = column[Option[String]]("ip")
	def ua = column[Option[String]]("ua")
	def created = column[Timestamp]("created")
	def last_access = column[Timestamp]("last_access")

	def * = (token, user, ip, ua, created, last_access) <> (Session.tupled, Session.unapply)
}

object Sessions extends TableQuery(new Sessions(_))
