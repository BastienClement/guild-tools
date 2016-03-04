package controllers.webtools

import actors.{AuthService, RosterService}
import controllers.webtools.WtController._
import models._
import models.mysql._
import play.api.mvc._
import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.CacheCell

object WtController {
	/**
	  * A collection of common names: servers, classes and races.
	  * Used to translate ids to human-readable strings.
	  */
	case class NamesCollection(s: Map[String, String], c: Map[Int, String], r: Map[Int, String])

	/**
	  * The wrapper request with user informations and session token.
	  * Also includes the list of user's characters and the common names mappings.
	  */
	class UserRequest[A](val user: User, val chars: Seq[Char], val names: NamesCollection,
			val token: String, val set_cookie: Boolean, val request: Request[A]) extends WrappedRequest[A](request)

	/**
	  * Indicates that the user is correctly authenticated but doesn't have access to the requested page
	  */
	object Deny extends Exception

	/**
	  * Indicates that the authentication failed
	  */
	object AuthFailed extends Exception

	/**
	  * Cache of common names mapping
	  */
	val names = CacheCell.async(10.minutes) {
		for {
			sn <- DB.run(sql"SELECT slug, name FROM gt_realms".as[(String, String)]).map(_.toMap)
			cn <- DB.run(sql"SELECT id, name FROM gt_classes".as[(Int, String)]).map(_.toMap)
			rn <- DB.run(sql"SELECT id, name FROM gt_races".as[(Int, String)]).map(_.toMap)
		} yield NamesCollection(sn, cn, rn)
	}

	/**
	  * Attempts to get User from session cookie.
	  */
	def cookieUser[A](implicit request: Request[A]) = {
		for {
			token <- Future(request.cookies.get("gt_session").get.value)
			user <- AuthService.userForSession(token)
		} yield (user, token, false)
	}

	/**
	  * Attempts to create a session from a PhpBB session cookie.
	  */
	def ssoUser[A](implicit request: Request[A]) = {
		for {
			token <- Future(request.getQueryString("sso").get)
			user <- DB.run {
				for {
					session <- PhpBBSessions.filter(_.token === token).result.head
					user <- PhpBBUsers.filter(u => u.id === session.user && u.group.inSet(AuthService.allowed_groups)).result.head
				} yield user
			}
			ip = Some(request.remoteAddress)
			ua = request.headers.get("User-Agent")
			session_token <- AuthService.createSession(user.id, ip, ua)
		} yield (user, session_token, true)
	}
}

/**
  * Common utilities for WebTool controllers.
  */
trait WtController {
	this: Controller =>

	/**
	  * The ActionBuilder for WebTools actions
	  */
	object UserAction extends ActionBuilder[UserRequest] {
		/**
		  * Wraps the request inside a UserRequest.
		  */
		def transform[A](implicit request: Request[A]) = {
			for {
				(user, token, set_token) <- cookieUser recoverWith { case _ => ssoUser }
				chars <- RosterService.chars(user.id)
				names_col <- names.value
			} yield new UserRequest(user, chars, names_col, token, set_token, request)
		}

		/**
		  * Action wrapper
		  */
		override def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[Result]) = {
			val result = for {
				req <- transform(request) recover { case _ => throw AuthFailed }
				res <- block(req)
			} yield {
				if (req.set_cookie) res.withCookies(Cookie("gt_session", req.token, maxAge = Some(60 * 60 * 24 * 7)))
				else res
			}

			result recover {
				case Deny => Redirect("/wt/")
				case AuthFailed => Ok(views.html.wt.unauthenticated.render(null))
			}
		}
	}
}
