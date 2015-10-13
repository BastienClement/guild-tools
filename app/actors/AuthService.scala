package actors

import actors.AuthService._
import models._
import models.mysql._
import reactive._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import utils.{Cache, SmartTimestamp}

object AuthService {
	val allowed_groups = Set(8, 12, 9, 11, 10, 13)
	val developer_users = Set(1647)
	val officier_groups = Set(11)
}

trait AuthService {
	// Perform user authentication based on the session token
	def auth(session: String): Future[User] = {
		val sess_query = Sessions.filter(_.token === session)
		sess_query.map(_.user).head flatMap { user_id =>
			sess_query.map(_.last_access).update(SmartTimestamp.now).run
			Users.filter(u => u.id === user_id && u.group.inSet(allowed_groups)).head
		}
	}

	// Cache of hash setting for every user. A random setting is generated if the user cannot be found.
	// NOTE: The intended security is flawed. The idea was that no failure would make it impossible to know whether
	// the user or password is wrong in case the login fail. The issue is that a fake setting is case-sensitive to
	// the username while a real setting is not.
	private val setting_cache = Cache.async[String, String](1.minute) { user =>
		val password = for (u <- Users if u.name === user || u.name_clean === user.toLowerCase) yield u.pass
		password.head map { pass =>
			pass.substring(0, 12)
		} fallbackTo {
			Future.successful("$H$9" + utils.randomToken().substring(0, 8))
		}
	}

	// Query the setting cache
	def setting(user: String): Future[String] = setting_cache(user)

	// Perform user authentication
	def login(name: String, password: String, salt: String): Future[String] = {
		val user_credentials = for {
			u <- Users if (u.name === name || u.name_clean === name.toLowerCase) && (u.group inSet allowed_groups)
		} yield (u.pass, u.id)

		user_credentials.headOption collect {
			case Some((pass_ref, user_id)) if password == utils.sha1(pass_ref + salt) =>
				def createSession(attempt: Int = 1): Option[String] = {
					val token = utils.randomToken()
					Try {
						val now = SmartTimestamp.now
						DB.run(Sessions.map(s => (s.token, s.user, s.ip, s.created, s.last_access)) +=(token, user_id, "", now, now)).await
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

	// Remove a session from the database
	def logout(session: String): Future[Unit] = DB.run {
		for (_ <- Sessions.filter(_.token === session).delete) yield ()
	}
}

class AuthServiceImpl extends AuthService
