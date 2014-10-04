package models

import mysql._
import java.sql.Timestamp

case class Session(token: String, user: Int, ip: String, created: Timestamp, last_access: Timestamp)

class Sessions(tag: Tag) extends Table[Session](tag, "gt_sessions") {
	def token = column[String]("token", O.PrimaryKey)
	def user = column[Int]("user")
	def ip = column[String]("ip")
	def created = column[Timestamp]("created")
	def last_access = column[Timestamp]("last_access")

	def * = (token, user, ip, created, last_access) <> (Session.tupled, Session.unapply)
}

object Sessions extends TableQuery(new Sessions(_))
