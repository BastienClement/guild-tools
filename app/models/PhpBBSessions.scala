package models

import models.mysql._

case class PhpBBSession(id: String, user: Int, ip: String, browser: String)

class PhpBBSessions(tag: Tag) extends Table[PhpBBSession](tag, "phpbb_sessions") {
	def id = column[String]("session_id", O.PrimaryKey)
	def user = column[Int]("session_user_id")
	def ip = column[String]("session_ip")
	def browser = column[String]("session_browser")

	def * = (id, user, ip, browser) <> (PhpBBSession.tupled, PhpBBSession.unapply)
}

object PhpBBSessions extends TableQuery(new PhpBBSessions(_))
