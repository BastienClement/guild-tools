package actors

import akka.actor.{ Actor, ActorRef, PoisonPill, actorRef2Scala }
import api._
import gt.{ Socket, Utils }
import gt.Global.ExecutionContext
import java.util.concurrent.atomic.AtomicInteger
import play.api.Logger
import play.api.libs.json._
import scala.concurrent.Future
import scala.util.{ Failure, Success }
import gt.User

class SocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor
	with AuthHandler
	with ChatHandler
	with ProfileHandler {
	// Debug socket ID
	val id = Utils.randomToken()

	// Protocol banner
	out ! Json.obj(
		"service" -> "GuildTools",
		"protocol" -> "GTP2",
		"version" -> "5.0")

	Logger.debug(s"Socket open: $remoteAddr-$id")

	// Current message handler
	type MessageDispatcher = (String, JsValue) => MessageResponse
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

	// Parallel requests counter
	val concurrentDispatch = new AtomicInteger(0)

	// Attached socket object
	var socket: Socket = null
	def user: User = socket.user

	def receive = {
		// Incoming message
		case message: JsValue => {
			val id = (message \ "#")
			Logger.debug(s">>> $message")

			val response = Future {
				concurrentDispatch.incrementAndGet()
				try {
					val cmd = (message \ "$").as[String]
					val arg = (message \ "&")
					dispatcher(cmd, arg)
				} finally {
					concurrentDispatch.decrementAndGet()
				}
			}

			response onComplete {
				case Success(msg) => {
					Logger.debug(s"<<< $msg")
					msg match {
						case _ if id == JsNull => {
							/* client is not interested by the result */
						}

						case MessageSuccess => {
							out ! Json.obj("$" -> "ack", "#" -> id, "&" -> JsNull)
						}

						case MessageResults(res) => {
							out ! Json.obj("$" -> "res", "#" -> id, "&" -> res)
						}

						case MessageFailure(err, message) => {
							out ! Json.obj(
								"$" -> "nok",
								"#" -> id,
								"&" -> Json.obj(
									"e" -> err,
									"m" -> message))
						}
					}
				}

				case Failure(e) => {
					out ! Json.obj(
						"$" -> "err",
						"#" -> id,
						"&" -> Json.obj(
							"e" -> e.getClass().getName(),
							"m" -> e.getMessage()))

					Logger.error("Fatal socket error", e)
					self ! PoisonPill
				}
			}
		}

		// Close message
		case CloseMessage(arg) => {
			out ! Json.obj("$" -> "close", "&" -> arg)
			self ! PoisonPill
		}

		// Outgoing message
		case Message(cmd, arg) => {
			val msg = Json.obj("$" -> cmd, "&" -> arg)
			Logger.debug(s"<<< $msg")
			out ! msg
		}
	}

	def unauthenticatedDispatcher: MessageDispatcher = {
		// Prevent concurrent requests before authentication
		case _ if (concurrentDispatch.get() > 1) => MessageFailure("ANON_CONCURRENT")

		case ("auth", arg) => handleAuth(arg)
		case ("auth:prepare", arg) => handleAuthPrepare(arg)
		case ("auth:login", arg) => handleAuthLogin(arg)

		case _ => MessageFailure("UNAVAILABLE")
	}

	def authenticatedDispatcher: MessageDispatcher = {
		case ("auth:logout", _) => handleAuthLogout()

		case ("chat:onlines", _) => handleChatOnlines()

		case ("profile:load", arg) => handleProfileLoad(arg)
		case ("profile:enable", arg) => handleProfileEnable(arg, true)
		case ("profile:disable", arg) => handleProfileEnable(arg, false)
		case ("profile:promote", arg) => handleProfilePromote(arg)
		case ("profile:remove", arg) => handleProfileRemove(arg)
		case ("profile:role", arg) => handleProfileRole(arg)
		case ("profile:check", arg) => handleProfileCheck(arg)
		case ("profile:register", arg) => handleProfileRegister(arg)

		case ("events:unbind", _) => handleEventUnbind()
		case _ => MessageFailure("UNAVAILABLE")
	}

	def handleEventUnbind(): MessageResponse = {
		socket.eventFilter = socket.FilterNone
		MessageSuccess
	}

	override def postStop(): Unit = {
		Logger.debug(s"Socket close: $remoteAddr-$id")
		if (socket != null) {
			socket.detach()
		}
	}
}
