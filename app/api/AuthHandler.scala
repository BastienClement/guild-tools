package api

import scala.concurrent.duration.DurationInt
import actors.Actors._
import actors.SocketHandler
import models._
import models.mysql._
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import play.api.libs.json.{JsNull, JsValue, Json}

trait AuthHandler {
	socket: SocketHandler =>

	object Auth {
		private var auth_salt = utils.randomToken()

		/**
		 * $:auth
		 */
		def handleAuth(arg: JsValue): MessageResponse = utils.atLeast[JsValue](250.milliseconds) {
			(arg \ "session").asOpt[String] flatMap { token =>
				session = Some(token)
				SessionManager.auth(token)
			} map { u =>
				user = u
				dispatcher = authenticatedDispatcher
				Json.obj("user" -> user, "ready" -> user.ready)
			} getOrElse {
				session = None
				Json.obj("socket" -> JsNull, "user" -> JsNull)
			}
		}

		/**
		 * $:auth:prepare
		 */
		def handlePrepare(arg: JsValue): MessageResponse = utils.atLeast(250.milliseconds) {
			val user = (arg \ "user").as[String].toLowerCase

			DB.withSession { implicit s =>
				val password = for (u <- Users if u.name === user || u.name_clean === user) yield u.pass
				password.firstOption map { pass =>
					val setting = pass.slice(0, 12)
					MessageResults(Json.obj("setting" -> setting, "salt" -> auth_salt))
				} getOrElse {
					MessageFailure("User not found")
				}
			}
		}

		/**
		 * $:auth:login
		 */
		def handleLogin(arg: JsValue): MessageResponse = utils.atLeast(500.milliseconds) {
			val user = (arg \ "user").as[String].toLowerCase
			val pass = (arg \ "pass").as[String].toLowerCase

			// Regenerate salt for next login
			val salt = auth_salt
			auth_salt = utils.randomToken()

			SessionManager.login(user, pass, salt) match {
				case Left(error) => MessageFailure(error)
				case Right(token) => MessageResults(token)
			}
		}

		/**
		 * $:auth:logout
		 */
		def handleLogout(arg: JsValue): MessageResponse = {
			for (id <- session) {
				SessionManager.logout(id)
				dispatcher = zombieDispatcher
				self ! CloseMessage("Logout")
			}

			MessageSuccess
		}
	}
}
