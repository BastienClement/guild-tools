package actors

import actors.AuthService._
import models._
import models.mysql._
import reactive._
import utils.{LazyCollection, SmartTimestamp}

import scala.concurrent.duration._
import scala.util.Try

object AuthService {
	val allowedGroups = Set(8, 12, 9, 11, 10, 13)
}

trait AuthService {
	private val settingCache = LazyCollection[String, Future[String]](1.minute) { user =>
		val password = for (u <- Users if u.name === user || u.name_clean === user.toLowerCase) yield u.pass
		password.head map { pass =>
			pass.substring(0, 12)
		} fallbackTo {
			"$H$9" + utils.randomToken().substring(0, 8)
		}
	}

	/**
	 * Perform user authentication based on the session token
	 */
	def auth(session: String): Future[User] = {
		val sess_query = Sessions.filter(_.token === session)
		sess_query.map(_.user).head flatMap { user_id =>
			sess_query.map(_.last_access).update(SmartTimestamp.now)
			Users.filter(u => u.id === user_id && u.group.inSet(allowedGroups)).head
		}
	}

	/**
	 * Get hash setting for user
	 */
	def setting(user: String): Future[String] = settingCache(user)

	/**
	 *
	 */
	def login(name: String, password: String, salt: String): Future[String] = {
		val user_credentials = for (u <- Users if (u.name === name || u.name_clean === name.toLowerCase) && (u.group inSet allowedGroups)) yield (u.pass, u.id)
		user_credentials.headOption collect {
			case Some((pass_ref, user_id)) if password == utils.sha1(pass_ref + salt) =>
				def createSession(attempt: Int = 1): Option[String] = {
					val token = utils.randomToken()
					Try {
						val now = SmartTimestamp.now
						DB.run(Sessions.map(s => (s.token, s.user, s.ip, s.created, s.last_access)) += (token, user_id, "", now, now)).await
						Some(token)
					} getOrElse {
						if (attempt < 3) createSession(attempt + 1)
						else None
					}
				}

				createSession() getOrElse {
					throw new Exception("Unable to login")
				}

			case _ => throw new Exception("Invalid credentials")
		}
	}

	/**
	 *
	 */
	def logout(session: String): Unit = {
		DB.run {
			Sessions.filter(_.token === session).delete
		}
	}
}

class AuthServiceImpl extends AuthService
