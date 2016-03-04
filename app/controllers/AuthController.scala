package controllers

import java.util.concurrent.ExecutionException

import actors.AuthService
import play.api.Play
import play.api.mvc.{Action, Controller, Cookie}
import reactive.ExecutionContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try
import utils.{TokenBucket, Cache}

class AuthController extends Controller {
	/** The path of the main auth page */
	def url(path: String) = (if (Play.isProd(Play.current)) "" else "/auth") + path

	/** Token buckets for rate-limiting the /sso page */
	private val sso_buckets = Cache(1.hour) { (source: String) => new TokenBucket(15, 10000) }

	/**
	  * Guesses the correct service URL for redirection after authentication.
	  *
	  * @param service The service code
	  */
	def serviceURL(service: String, default: String = url("/account")) = {
		val prod = Play.isProd(Play.current)
		val dev = Play.isDev(Play.current)

		service match {
			case "www" => "https://www.fs-guild.net/sso.php"

			case "gt" if prod => "https://gt.fs-guild.net/sso"
			case "gt" if dev => "/sso"

			case _ => default
		}
	}

	/**
	  * Constructs the session cookie.
	  *
	  * @param session The session token
	  */
	def sessionCookie(session: String) = Cookie(
		name = "FSID",
		value = session,
		maxAge = Some(60 * 60 * 24 * 5),
		httpOnly = true,
		secure = true
	)

	/**
	  * Main login form.
	  */
	def main() = Action.async { req =>
		val valid = req.cookies.get("FSID") match {
			case Some(cookie) => AuthService.sessionActive(cookie.value)
			case None => Future.successful(false)
		}

		valid.map {
			case true => Redirect(url("/account"))
			case false => Ok(views.html.auth.main.render(req.flash.get("error").orElse(req.getQueryString("error"))))
		}
	}

	/**
	  * Perform authentication.
	  * This action is indirectly rate-limited by the call to AuthService.login
	  */
	def auth() = Action.async { req =>
		utils.atLeast(1.seconds) {
			Try {
				val post = req.body.asFormUrlEncoded.get.map { case (k, v) => (k, v.headOption.getOrElse("")) }
				val user = post("user").trim
				val pass = post("pass").trim

				AuthService.login(user, pass, Some(req.remoteAddress), req.headers.get("User-Agent")).map { session =>
					Redirect(serviceURL(req.session.get("service").getOrElse("")), Map(
						"session" -> Seq(session),
						"token" -> Seq(req.session.get("token").getOrElse(""))
					)).withCookies(sessionCookie(session)).withNewSession
				}.recover {
					case e: ExecutionException => Redirect(url("/")).flashing("error" -> e.getCause.getMessage)
					case e => Redirect(url("/")).flashing("error" -> e.getMessage)
				}
			}.getOrElse {
				Future.successful(Redirect(url("/")).flashing("error" -> "An unknown error occured"))
			}
		}
	}

	/**
	  * Single sign-on page.
	  * Will redirect the user back to the service if the session is valid or
	  * display the login page if it is not.
	  */
	def sso() = Action.async { req =>
		val service = req.getQueryString("service").getOrElse("")
		val token = req.getQueryString("token").getOrElse("")
		val session = req.cookies.get("FSID")
		val ignore = req.getQueryString("noauth").isDefined

		// Current session is valid, redirect to service
		def success_redirect =
			Redirect(serviceURL(service), Map(
				"session" -> Seq(session.get.value),
				"token" -> Seq(token)
			)).withCookies(sessionCookie(session.get.value))

		// Current session is invalid, but the service does not want authentication
		def ignore_redirect =
			Redirect(serviceURL(service, url("/")), Map(
				"session" -> Seq(""),
				"token" -> Seq(token)
			))

		// Current session is invalid and service is requesting authentication
		def auth_redirect =
			Redirect(url("/")).withSession {
				req.session + ("service" -> service) + ("token" -> token)
			}

		if (sso_buckets(req.remoteAddress).take()) {
			val valid = session match {
				case Some(cookie) => AuthService.sessionActive(cookie.value)
				case None => Future.successful(false)
			}

			valid.map {
				case true => success_redirect
				case false if ignore => ignore_redirect
				case false => auth_redirect
			}
		} else if (req.getQueryString("noauth").isDefined) {
			Future.successful(ignore_redirect)
		} else {
			Future.successful(Redirect(url("/throttled")))
		}
	}

	/**
	  * Logouts
	  */
	def logout() = Action.async { req =>
		val service = req.getQueryString("service").getOrElse("")
		val token = req.getQueryString("token").getOrElse("")
		req.cookies.get("FSID").map { cookie =>
			AuthService.logout(cookie.value)
		}.getOrElse(Future.successful(())).map { _ =>
			Redirect(serviceURL(service, url("/")), Map(
				"logout" -> Seq("yes"),
				"token" -> Seq(token)
			)).withCookies(Cookie("FSID", ""))
		}
	}

	/**
	  * Throttled
	  */
	def throttled() = Action { Ok(views.html.auth.throttled.render()) }
}
