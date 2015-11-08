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
	/** The set of every groups allowed to login */
	val allowed_groups = Set(
		8, // Apply
		12, // Casual
		9, // Member
		11, // Officer
		10, // Guest
		13 // Veteran
	)

	/** The set of every user having developer rights */
	val developer_users = Set(1647)

	/** The set of every groups considered officer */
	val officier_groups = Set(11)

	/** The set of every groups considered guild members */
	val member_groups = Set(9, 11)

	/** The set of every groups forming the guild roster */
	val roster_groups = Set(8, 9, 11)

	/** The set of every groups considered part of the guild */
	val fromscratch_groups = allowed_groups

	/**
	  * Cache of users corresponding to session tokens.
	  */
	val userForSession = Cache.async[String, User](15.seconds) { token =>
		val query = for {
			session <- Sessions.filter(_.token === token).result.head
			user <- Users.filter(_.id === session.user).result.head
		} yield {
			user
		}
		query.run
	}
}

/**
  * A static actor providing auth and login services.
  */
trait AuthService {
	/**
	  * Returns the user corresponding to a session token.
	  */
	def auth(session: String): Future[User] = {
		val sess_query = Sessions.filter(_.token === session)
		sess_query.map(_.user).head flatMap { user_id =>
			sess_query.map(_.last_access).update(SmartTimestamp.now).run
			Users.filter(u => u.id === user_id && u.group.inSet(allowed_groups)).head
		}
	}

	/**
	  * Cache of hash setting for every user.
	  * @todo Fix case-sensitiveness if user cannot be found
	  */
	private val setting_cache = Cache.async[String, String](1.minute) { user =>
		val password = for (u <- Users if u.name === user || u.name_clean === user.toLowerCase) yield u.pass
		password.head map { pass =>
			pass.substring(0, 12)
		} recover {
			case _ => "$H$9" + utils.randomToken().substring(0, 8)
		}
	}

	/**
	  * Returns the user's hash settings.
	  * If the user cannot be found, a random string is generated instead.
	  */
	def setting(username: String): Future[String] = setting_cache(username)

	/**
	  * Perform user authentication and return a new session token.
	  */
	def login(username: String, password: String, salt: String, ip: Option[String], ua: Option[String]): Future[String] = {
		val user_credentials = for {
			u <- Users if (u.name === username || u.name_clean === username.toLowerCase) && (u.group inSet allowed_groups)
		} yield (u.pass, u.id)

		user_credentials.headOption flatMap {
			case Some((pass_ref, user_id)) if password == utils.sha1(pass_ref + salt) =>
				createSession(user_id, ip, ua) recover {
					case _ => throw new Exception("Unable to login")
				}

			case _ => throw new Exception("Invalid credentials")
		}
	}

	/**
	  * Create a session for a given user.
	  * Unlink `login`, this method does not perform authentication and always succeed.
	  */
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

	/**
	  * Remove a session from the database.
	  * @todo Kill every socket open with this session
	  */
	def logout(session: String): Future[Unit] = DB.run {
		for (_ <- Sessions.filter(_.token === session).delete) yield ()
	}
}
