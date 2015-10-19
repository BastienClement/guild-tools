package controllers

import actors._
import models._
import models.mysql._
import play.api.mvc._
import reactive._
import scala.concurrent.Future

object WebTools extends Controller {
	class UserRequest[A](val user: User, val token: String, val set_cookie: Boolean, val request: Request[A]) extends WrappedRequest[A](request)

	object UserAction extends ActionBuilder[UserRequest] {
		def cookieUser[A](implicit request: Request[A]) = {
			for {
				token <- Future {
					request.cookies.get("gt_session").get.value
				}
				session <- Sessions.filter(_.token === token).head
				user <- Users.filter(_.id === session.user).head
			} yield (user, token, false)
		}

		def ssoUser[A](implicit request: Request[A]) = {
			for {
				token <- Future {
					request.getQueryString("sso").get
				}
				(session, user) <- DB.run {
					for {
						session <- PhpBBSessions.filter(_.id === token).result.head
						user <- Users.filter(u => u.id === session.user && u.group.inSet(AuthService.allowed_groups)).result.head
					} yield (session, user)
				}
				session_token <- Actors.AuthService.createSession(user.id, Some(request.remoteAddress), request.headers.get("User-Agent"))
			} yield (user, session_token, true)
		}

		def transform[A](implicit request: Request[A]) = {
			for {
				(user, token, set_token) <- cookieUser recoverWith { case _ => ssoUser }
			} yield new UserRequest(user, token, set_token, request)
		}

		override def invokeBlock[A](request: Request[A], block: (UserRequest[A]) => Future[Result]) = {
			val result = for {
				req <- transform(request)
				res <- block(req)
			} yield {
				if (req.set_cookie) res.withCookies(Cookie("gt_session", req.token, maxAge = Some(60 * 60 * 24 * 7)))
				else res
			}

			result recover { case _ => Ok(views.html.wt.unauthenticated.render()) }
		}
	}

	def main = UserAction { request =>
		Ok(views.html.wt.main.render(request.user))
	}
}
