package api

import java.sql.SQLException

import scala.annotation.tailrec

import play.api.Play.current
import play.api.db.DB
import play.api.libs.json.{ JsNull, JsValue, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import actors.SocketHandler
import anorm.ParameterValue.toParameterValue
import anorm.SqlStringInterpolation
import gt.{ Socket, User, Utils }

trait AuthHandler { this: SocketHandler =>
	private var auth_salt = Utils.randomToken()

	/**
	 * $:auth
	 */
	def handleAuth(arg: JsValue): MessageResponse = {
		// Attach this handler to the requested socket if available
		def resumeSocket(socket_id: Option[String]) = {
			socket_id flatMap {
				Socket.findByID(_)
			} map { socket =>
				socket.updateHandler(self)
				this.socket = Some(socket)
				this.dispatcher = authenticatedDispatcher
				Json.obj("resume" -> true)
			}
		}

		// Find user by session and create a new socket for it
		def resumeSession(session_id: Option[String]) = {
			session_id.flatMap({
				User.findBySession(_)
			}).map(user => {
				val socket = user.createSocket(self)
				this.socket = Some(socket)
				this.dispatcher = authenticatedDispatcher
				Json.obj(
					"socket" -> socket.token,
					"user" -> socket.user.toJson()
				)
			})
		}

		val socket_id = (arg \ "socket").asOpt[String]
		val session_id = (arg \ "session").asOpt[String]

		val results =
			resumeSocket(socket_id)
				.orElse({ resumeSession(session_id) })
				.getOrElse({
					Json.obj("socket" -> JsNull, "user" -> JsNull)
				})

		MessageResults(results)
	}

	/**
	 * $:login:prepare
	 */
	def handleLoginPrepare(arg: JsValue): MessageResponse = {
		val user = (arg \ "user").as[String]

		Thread.sleep(500)
		DB.withConnection { implicit c =>
			SQL"SELECT user_password FROM phpbb_users WHERE username_clean = $user LIMIT 1"
				.singleOpt()
				.map(row => {
					val passwd = row[String]("user_password")
					val setting = passwd.slice(0, 12)
					MessageResults(Json.obj("setting" -> setting, "salt" -> auth_salt))
				})
				.getOrElse({
					MessageFailed("USER_NOT_FOUND")
				})
		}
	}

	/**
	 * $:login:exec
	 */
	def handleLoginExec(arg: JsValue): MessageResponse = {
		val user = (arg \ "user").as[String]
		val pass = (arg \ "pass").as[String]

		// Regenerate salt for next login
		val salt = auth_salt
		auth_salt = Utils.randomToken()

		DB.withConnection { implicit c =>
			SQL"SELECT user_id, user_password FROM phpbb_users WHERE username_clean = $user LIMIT 1"
				.singleOpt()
				.filter(row => {
					pass == Utils.md5(row[String]("user_password") + salt)
				})
				.map(row => {
					val user_id = row[Int]("user_id")

					@tailrec def createSession(attempt: Int = 1): Option[String] = {
						val session = Utils.randomToken()
						val query =
							SQL"""
								INSERT INTO gt_sessions SET token = $session, user = $user_id,
								ip = $remoteAddr,created = NOW(), last_access = NOW()
							"""

						try {
							query.executeInsert()
							Some(session)
						} catch {
							case e: SQLException =>
								if (attempt < 3) {
									createSession(attempt + 1)
								} else {
									None
								}
						}
					}

					createSession()
						.map(id => {
							MessageResults(Json.obj("session" -> id))
						})
						.getOrElse({
							MessageFailed("UNABLE_TO_LOGIN")
						})
				})
				.getOrElse({
					MessageFailed("INVALID_CREDENTIALS")
				})
		}
	}
}