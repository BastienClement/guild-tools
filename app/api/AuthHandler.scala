package api

import actors.SocketHandler
import anorm.ParameterValue.toParameterValue
import anorm.SqlStringInterpolation
import gt.{ Socket, User, Utils }
import java.sql.SQLException
import play.api.Play.current
import play.api.db.DB
import play.api.libs.json.{ JsNull, JsValue, Json }
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import scala.annotation.tailrec
import scala.concurrent.duration.DurationInt

trait AuthHandler { this: SocketHandler =>
	private var auth_salt = Utils.randomToken()

	/**
	 * $:auth
	 */
	def handleAuth(arg: JsValue): MessageResponse = Utils.atLeast(250.milliseconds) {
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
			session_id flatMap { session =>
				User.findBySession(session) map (_.createSocket(session, self))
			} map { socket =>
				this.socket = Some(socket)
				this.dispatcher = authenticatedDispatcher
				Json.obj(
					"socket" -> socket.token,
					"user" -> socket.user.toJson()
				)
			}
		}

		val socket_id = (arg \ "socket").asOpt[String]
		val session_id = (arg \ "session").asOpt[String]

		val success = resumeSocket(socket_id) orElse (resumeSession(session_id))
		val result = success getOrElse (Json.obj("socket" -> JsNull, "user" -> JsNull))

		MessageResults(result)
	}

	/**
	 * $:login:prepare
	 */
	def handleLoginPrepare(arg: JsValue): MessageResponse = Utils.atLeast(250.milliseconds) {
		val user = (arg \ "user").as[String].toLowerCase

		DB.withConnection { implicit c =>
			val q = SQL"SELECT user_password FROM phpbb_users WHERE username_clean = $user LIMIT 1"
			q.singleOpt() map { row =>
				val passwd = row[String]("user_password")
				val setting = passwd.slice(0, 12)
				MessageResults(Json.obj("setting" -> setting, "salt" -> auth_salt))
			} getOrElse {
				MessageFailure("USER_NOT_FOUND")
			}
		}
	}

	/**
	 * $:login:exec
	 */
	def handleLoginExec(arg: JsValue): MessageResponse = Utils.atLeast(500.milliseconds) {
		val user = (arg \ "user").as[String].toLowerCase
		val pass = (arg \ "pass").as[String].toLowerCase

		// Regenerate salt for next login
		val salt = auth_salt
		auth_salt = Utils.randomToken()

		DB.withConnection { implicit c =>
			val q = SQL"SELECT user_id, user_password FROM phpbb_users WHERE username_clean = $user LIMIT 1"
			q.singleOpt() filter { row =>
				pass == Utils.md5(row[String]("user_password") + salt)
			} map { row =>
				val user_id = row[Int]("user_id")

				@tailrec def createSession(attempt: Int = 1): Option[String] = {
					val session = Utils.randomToken()
					val query = SQL"INSERT INTO gt_sessions SET token = $session, user = $user_id, ip = $remoteAddr,created = NOW(), last_access = NOW()"

					try {
						query.executeInsert()
						Some(session)
					} catch {
						case e: SQLException => {
							if (attempt < 3)
								createSession(attempt + 1)
							else
								None
						}
					}
				}

				createSession() map { s =>
					MessageResults(Json.obj("session" -> s))
				} getOrElse {
					MessageFailure("UNABLE_TO_LOGIN")
				}
			} getOrElse {
				MessageFailure("INVALID_CREDENTIALS")
			}
		}
	}

	/**
	 * $:logout
	 */
	def handleLogout(): MessageResponse = {
		this.socket match {
			case Some(socket) => {
				DB.withConnection { implicit c =>
					SQL"DELETE FROM gt_sessions WHERE token = ${socket.session} LIMIT 1".execute()
					this.socket = None
					this.dispatcher = unauthenticatedDispatcher
					MessageSuccess()
				}
			}

			case None => {
				MessageFailure("NO_SESSION")
			}
		}

	}
}