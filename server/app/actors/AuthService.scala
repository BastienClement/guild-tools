package actors

import akka.actor.ActorRef
import models.authentication.{Session, Sessions, Users}
import models.{PhpBBUsers, User}
import reactive._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
import utils.Implicits._
import utils.SlickAPI._
import utils.{DateTime, _}
import utils.crypto.Hasher

private[actors] class AuthServiceImpl extends AuthService

object AuthService extends StaticActor[AuthService, AuthServiceImpl]("AuthService") with PubSub[String] {
	/** Message sent to subscribers when a session is closed */
	case object SessionClosed

	/**
	  * Cache of users corresponding to session tokens.
	  */
	val userForSession = Cache.async[String, User](15.seconds) { token =>
		Sessions.filter(_.token === token).map(_.user).head.flatMap { user_id =>
			PhpBBUsers.filter(u => u.id === user_id).head
		}
	}

	/**
	  * Returns the user corresponding to a session token.
	  * Provided for compatibility.
	  */
	def auth(session: String, ip: Option[String] = None, ua: Option[String] = None): Future[User] = {
		userForSession(session) andThen {
			case Success(user) =>
				for (sess <- Sessions.filter(_.token === session).head) {
					Sessions.filter(_.token === session).map {
						s => (s.last_access, s.ip, s.ua)
					}.update {
						(DateTime.now, ip.orElse(sess.ip), ua.orElse(sess.ua))
					}.run
				}
		}
	}

	/**
	  * Returns the user corresponding to a session token and register the
	  * calling actor as a subscriber for the SessionClosed message.
	  *
	  * @param session The session token
	  * @param sub     The calling actor
	  * @return The user associated to the session
	  */
	def authAndSubscribe(session: String, ip: Option[String] = None, ua: Option[String] = None)
	                    (implicit sub: ActorRef): Future[User] = {
		auth(session, ip, ua).andThen { case Success(_) => subscribe(sub, session) }
	}

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
	/** User buckets */
	private val buckets = Cache(1.hour) { (user: String) => new TokenBucket(10, 15000) }

	/**
	  * Performs user authentication and return a new session token.
	  *
	  * @param username The user's login name
	  * @param password The user's password (plain text)
	  * @param ip       Remote IP address
	  * @param ua       Remote User Agent
	  */
	def login(username: String, password: String, ip: Option[String], ua: Option[String]): Future[String] = {
		val pass = password.substring(0, 100.min(password.length))
		val bucket = buckets(username.toLowerCase)

		if (!bucket.take()) {
			StacklessException("Authentication is not available right now.")
		} else {
			val credentials = Users.findByUsername(username).map(u => (u.password, u.id)).head.recoverWith {
				case _ => PhpBBUsers.findByUsername(username).map(u => (u.pass, u.id)).head
			}

			credentials.flatMap {
				case (pass_ref, user_id) if Hasher.checkPassword(pass, pass_ref) =>
					if (true || pass_ref.startsWith("$H$")) Users.upgradeAccount(user_id, pass)
					createSession(user_id, ip, ua)
					.andThen { case Success(_) => bucket.put() }
					.otherwise("Unable to login")
			}.otherwise("Invalid username or password")
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
			val now = DateTime.now
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
	def logout(session: String): Future[Unit] = {
		val del = for (_ <- Sessions.filter(_.token === session).delete) yield ()
		del.run.andThen { case _ =>
			AuthService.userForSession.clear(session)
			AuthService.publish(AuthService.SessionClosed, _ == session)
		}
	}
}
