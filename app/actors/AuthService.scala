package actors

import actors.AuthService._
import models._
import models.mysql._
import reactive._
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.{Cache, SmartTimestamp}

private[actors] class AuthServiceImpl extends AuthService

object AuthService extends StaticActor[AuthService, AuthServiceImpl]("AuthService") {
	val allowed_groups = Set(
		8, // Apply
		12, // Casual
		9, // Member
		11, // Officer
		10, // Guest
		13 // Veteran
	)

	val developer_users = Set(1647)
	val officier_groups = Set(11)
	val member_groups = Set(9, 11)
	val roster_groups = Set(8, 9, 11)
	val fromscratch_groups = allowed_groups
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
		} recover {
			case _ => "$H$9" + utils.randomToken().substring(0, 8)
		}
	}

	// Query the setting cache
	def setting(user: String): Future[String] = setting_cache(user)

	// Perform user authentication
	def login(name: String, password: String, salt: String, ip: Option[String], ua: Option[String]): Future[String] = {
		val user_credentials = for {
			u <- Users if (u.name === name || u.name_clean === name.toLowerCase) && (u.group inSet allowed_groups)
		} yield (u.pass, u.id)

		user_credentials.headOption flatMap {
			case Some((pass_ref, user_id)) if password == utils.sha1(pass_ref + salt) =>
				createSession(user_id, ip, ua) recover {
					case _ => throw new Exception("Unable to login")
				}

			case _ => throw new Exception("Invalid credentials")
		}
	}

	// Create a session for a given user
	def createSession(user: Int, ip: Option[String], ua: Option[String]): Future[String] = {
		def attempt(count: Int = 1): Future[String] = {
			val token = utils.randomToken()
			val now = SmartTimestamp.now
			val res = for {
				_ <- DB.run(Sessions += Session(token, user, ip, ua, now, now))
			} yield token

			res recoverWith {
				case _ if count < 3 => attempt(count + 1)
			}
		}

		attempt()
	}

	// Remove a session from the database
	def logout(session: String): Future[Unit] = DB.run {
		for (_ <- Sessions.filter(_.token === session).delete) yield ()
	}
}
