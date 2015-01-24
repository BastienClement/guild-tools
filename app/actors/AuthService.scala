package actors

import scala.concurrent.duration._
import scala.util.Try
import actors.AuthService._
import models._
import models.mysql._
import utils.{LazyCollection, SmartTimestamp}

object AuthService {
	val allowedGroups = Set(8, 12, 9, 11, 10)
}

trait AuthService {
	def auth(session: String): Option[User]
	def login(name: String, password: String, salt: String): Either[String, String]
	def logout(session: String): Unit
}

class AuthServiceImpl extends AuthService {
	/**
	 *
	 */
	private val sessionCache = LazyCollection(1.minute) { (session: String) =>
		DB.withSession { implicit s =>
			val sess_query = Sessions.filter(_.token === session)
			sess_query.map(_.user).firstOption flatMap { user_id =>
				sess_query.map(_.last_access).update(SmartTimestamp.now)
				Users.filter(u => u.id === user_id && u.group.inSet(allowedGroups)).firstOption
			}
		}
	}

	/**
	 * Perform user authentication based on the session token
	 */
	def auth(session: String): Option[User] = sessionCache(session)

	/**
	 *
	 */
	def login(name: String, password: String, salt: String): Either[String, String] = {
		DB.withSession { implicit s =>
			val user_credentials = for (u <- Users if (u.name === name || u.name_clean === name) && (u.group inSet allowedGroups)) yield (u.pass, u.id)
			user_credentials.firstOption filter {
				case (pass_ref, user_id) =>
					password == utils.md5(pass_ref + salt)
			} map {
				case (pass_ref, user_id) =>
					def createSession(attempt: Int = 1): Option[String] = {
						val token = utils.randomToken()
						Try {
							val now = SmartTimestamp.now
							Sessions.map { s =>
								(s.token, s.user, s.ip, s.created, s.last_access)
							} insert {
								(token, user_id, "", now, now)
							}
							Some(token)
						} getOrElse {
							if (attempt < 3) createSession(attempt + 1)
							else None
						}
					}

					createSession() map { s =>
						Right(s)
					} getOrElse {
						Left("Unable to login")
					}
			} getOrElse {
				Left("Invalid credentials")
			}
		}
	}

	/**
	 *
	 */
	def logout(session: String): Unit = {
		DB.withSession { implicit s =>
			Sessions.filter(_.token === session).delete
			sessionCache.clear(session)
		}
	}
}
