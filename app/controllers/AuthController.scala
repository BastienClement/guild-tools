package controllers

import java.util.concurrent.ExecutionException

import actors.AuthService
import play.api.Play
import play.api.mvc.{Action, Controller, Cookie}
import reactive.ExecutionContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Try

class AuthController extends Controller {
	/** The path of the main auth page */
	def url(path: String) = (if (Play.isProd(Play.current)) "" else "/auth") + path

	/**
	  * Guesses the correct service URL for redirection after authentication.
	  *
	  * @param service The service code
	  */
	def serviceURL(service: String, default: String = url("/account")) = {
		val prod = Play.isProd(Play.current)
		val dev = Play.isDev(Play.current)

		service match {
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
		name = "FS_SESSION",
		value = session,
		maxAge = Some(60 * 60 * 24 * 7),
		httpOnly = true,
		secure = true
	)

	/**
	  * Main login form.
	  */
	def main() = Action.async { req =>
		val valid = req.cookies.get("FS_SESSION") match {
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
	  */
	def auth() = Action.async { req =>
		utils.atLeast(2.seconds) {
			Try {
				val post = req.body.asFormUrlEncoded.get.map { case (k, v) => (k, v.headOption.getOrElse("")) }
				val user = post("user").trim
				val pass = post("pass").trim

				AuthService.login(user, pass, Some(req.remoteAddress), req.headers.get("User-Agent")).map { session =>
					Redirect(serviceURL(req.session.get("service").getOrElse("")), Map(
						"session" -> Seq(session),
						"token" -> Seq(req.session.get("token").getOrElse(""))
					)).withCookies(sessionCookie(session))
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
		utils.atLeast(1.seconds) {
			val service = req.getQueryString("service").getOrElse("")
			val token = req.getQueryString("token").getOrElse("")
			val session = req.cookies.get("FS_SESSION")

			val valid = session match {
				case Some(cookie) => AuthService.sessionActive(cookie.value)
				case None => Future.successful(false)
			}

			valid.map {
				case true =>
					Redirect(serviceURL(service), Map(
						"session" -> Seq(session.get.value),
						"token" -> Seq(token)
					)).withCookies(sessionCookie(session.get.value))

				case false if req.getQueryString("noauth").isDefined =>
					Redirect(serviceURL(service, url("/")), Map(
						"session" -> Seq(""),
						"token" -> Seq(token)
					))

				case false =>
					Redirect(url("/")).withSession {
						req.session + ("service" -> service) + ("token" -> token)
					}
			}
		}
	}

	/**
	  * Logouts
	  */
	def logout() = Action.async { req =>
		val service = req.getQueryString("service").getOrElse("")
		val token = req.getQueryString("token").getOrElse("")
		req.cookies.get("FS_SESSION").map { cookie =>
			AuthService.logout(cookie.value)
		}.getOrElse(Future.successful(())).map { _ =>
			Redirect(serviceURL(service), Map(
				"logout" -> Seq("yes"),
				"token" -> Seq(token)
			)).withCookies(Cookie("FS_SESSION", ""))
		}
	}
}
