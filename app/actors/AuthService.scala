package actors

import models._
import models.mysql._
import reactive._
import utils.{Cache, Phpass, SmartTimestamp}

import scala.concurrent.Future
import scala.concurrent.duration._

private[actors] class AuthServiceImpl extends AuthService

object AuthService extends StaticActor[AuthService, AuthServiceImpl]("AuthService") {
	/** The set of every user having developer rights */
	val developer_users = Set(1647)

	/** The set of every groups considered officer */
	val officier_groups = Set(11)

	/** The set of every groups considered guild members */
	val member_groups = Set(9, 11)

	/** The set of every groups forming the guild roster */
	val roster_groups = Set(8, 9, 11)

	/** The set of every groups considered part of the guild */
	val fromscratch_groups = Set(
		8, // Apply
		12, // Casual
		9, // Member
		11, // Officer
		10, // Guest
		13 // Veteran
	)

	/** The set of every groups allowed to login */
	val allowed_groups = fromscratch_groups

	/**
	  * Cache of users corresponding to session tokens.
	  */
	val userForSession = Cache.async[String, User](15.seconds) { token =>
		val sess_query = Sessions.filter(_.token === token)
		sess_query.map(_.user).head.flatMap { user_id =>
			sess_query.map(_.last_access).update(SmartTimestamp.now).run
			Users.filter(u => u.id === user_id).head
		}
	}

	/**
	  * Returns the user corresponding to a session token.
	  * Provided for compatibility.
	  */
	def auth(session: String): Future[User] = userForSession(session)

	/**
	  * Checks if a given session is active and valid.
	  *
	  * @param session The session ID
	  */
	def sessionActive(session: String): Future[Boolean] = {
		userForSession(session).map { _ => true }.recover { case _ => false }
	}
}

/**
  * A static actor providing auth and login services.
  */
trait AuthService {
	/**
	  * Performs user authentication and return a new session token.
	  *
	  * @param username  The user's login name
	  * @param password  The user's password (raw)
	  * @param ip        Remote IP address
	  * @param ua        Remote User Agent
	  */
	def login(username: String, password: String, ip: Option[String], ua: Option[String]): Future[String] = {
		val user_credentials = for {
			u <- Users if u.name === username || u.name_clean === username.toLowerCase
		} yield (u.pass, u.id)

		val hash = new Phpass()

		user_credentials.headOption flatMap {
			case Some((pass_ref, user_id)) if hash.isMatch(password, pass_ref) =>
				createSession(user_id, ip, ua) recover {
					case e => throw new Exception("Unable to login")
				}

			case e => throw new Exception("Invalid credentials")
		}
	}

	/**
	  * Creates a session for a given user.
	  * Unlink `login`, this method does not perform authentication and always succeed.
	  *
	  * @param user The user id
	  * @param ip   Remote IP during login
	  * @param ua   Remote User-Agent
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
	  * Removes a session from the database.
	  *
	  * @todo Kill every socket open with this session
	  */
	def logout(session: String): Future[Unit] = DB.run {
		for (_ <- Sessions.filter(_.token === session).delete) yield ()
	}
}
