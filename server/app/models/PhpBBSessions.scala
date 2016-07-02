package models

import models.mysql._

/**
  * A phpBB session
  * @param token     the session token
  * @param user      the user id
  * @param ip        the remote IP address
  * @param browser   the user-agent string
  */
case class PhpBBSession(token: String, user: Int, ip: String, browser: String)

/**
  * phpbb_sessions table definition
  */
class PhpBBSessions(tag: Tag) extends Table[PhpBBSession](tag, "phpbb_sessions") {
	def token = column[String]("session_id", O.PrimaryKey)
	def user = column[Int]("session_user_id")
	def ip = column[String]("session_ip")
	def browser = column[String]("session_browser")

	def * = (token, user, ip, browser) <> (PhpBBSession.tupled, PhpBBSession.unapply)
}

/**
  * TableQuery for PhpBB sessions
  */
object PhpBBSessions extends TableQuery(new PhpBBSessions(_))
