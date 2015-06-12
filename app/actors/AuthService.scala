package actors

import scala.concurrent.duration._
import scala.util.Try
import actors.AuthService._
import models._
import models.mysql._
import reactive._
import utils.{LazyCollection, SmartTimestamp}

object AuthService {
	val allowedGroups = Set(8, 12, 9, 11, 10, 13)
}

trait AuthService {
	def auth(session: String): Future[User]
	def setting(user: String): Future[String]
	def login(name: String, password: String, salt: String): Either[String, String]
	def logout(session: String): Unit
}

class AuthServiceImpl extends AuthService {
	private val sessionCache = LazyCollection(1.minute) { (session: String) =>
		val sess_query = Sessions.filter(_.token === session)
		sess_query.map(_.user).head flatMap { user_id =>
			sess_query.map(_.last_access).update(SmartTimestamp.now)
			Users.filter(u => u.id === user_id && u.group.inSet(allowedGroups)).head
		}
	}

	private val settingCache = LazyCollection(1.minute) { (user: String) =>
		val password = for (u <- Users if u.name === user || u.name_clean === user) yield u.pass
		password.head map { pass =>
			pass.substring(0, 12)
		} fallbackTo {
			"$H$9" + utils.randomToken().substring(0, 8)
		}
	}

	/**
	 * Perform user authentication based on the session token
	 */
	def auth(session: String): Future[User] = sessionCache(session)

	/**
	 * Get hash setting for user
	 */
	def setting(user: String): Future[String] = settingCache(user)

	/**
	 *
	 */
	def login(name: String, password: String, salt: String): Either[String, String] = {
		val user_credentials = for (u <- Users if (u.name === name || u.name_clean === name) && (u.group inSet allowedGroups)) yield (u.pass, u.id)
		user_credentials.headOption.get filter {
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
						} += {
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
