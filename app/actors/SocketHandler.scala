package actors

import akka.actor.{Actor, ActorRef, PoisonPill, actorRef2Scala}
import api.{AuthHandler, CloseMessage, Message, MessageFailure, MessageResponse, MessageResults, MessageSilent, MessageSuccess, ProfileHandler}
import gt.{Socket, Utils}
import gt.Global.ExecutionContext
import java.util.concurrent.atomic.AtomicInteger
import play.api.Logger
import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.libs.json.Json.toJsFieldJsValueWrapper
import scala.concurrent.Future
import scala.util.{Failure, Success}

class SocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor
	with AuthHandler
	with ProfileHandler
{
	// Debug socket ID
	val id = Utils.randomToken()

	// Protocol banner
	out ! Json.obj(
		"service" -> "GuildTools",
		"protocol" -> "GTP2",
		"version" -> "5.0")

	Logger.debug(s"Socket open: $remoteAddr-$id")

	// Current message handler
	type MessageDispatcher = PartialFunction[Message, MessageResponse]
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

	// Parallel requests counter
	val concurrentDispatch = new AtomicInteger(0)

	// Attached socket object
	var socket: Option[Socket] = None

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
					dispatcher(Message(cmd, arg))
				} finally {
					concurrentDispatch.decrementAndGet()
				}
			}

			response onComplete {
				case Success(msg) => {
					Logger.debug(s"<<< $msg")
					msg match {
						case MessageSuccess() =>
							out ! Json.obj("$" -> "ack", "#" -> id, "&" -> JsNull)
						case MessageResults(res) =>
							out ! Json.obj("$" -> "res", "#" -> id, "&" -> res)
						case MessageFailure(err, message) =>
							out ! Json.obj(
								"$" -> "nok",
								"#" -> id,
								"&" -> Json.obj(
									"e" -> err,
									"m" -> message))
						case MessageSilent() => /* silent */
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
			out ! Json.obj("$" -> "close", "&" -> arg, "#" -> JsNull)
			self ! PoisonPill
		}

		// Outgoing message
		case Message(cmd, arg) => {
			out ! Json.obj("$" -> cmd, "&" -> arg, "#" -> JsNull)
		}
	}

	def unauthenticatedDispatcher: MessageDispatcher = {
		// Prevent concurrent requests before authentication
		case _ if (concurrentDispatch.get() > 1) => MessageFailure("ANON_CONCURRENT")

		// Auth
		case Message("auth", arg) => handleAuth(arg)
		case Message("login:prepare", arg) => handleLoginPrepare(arg)
		case Message("login:exec", arg) => handleLoginExec(arg)

		// Default
		case _ => MessageFailure("UNAVAILABLE")
	}

	def authenticatedDispatcher: MessageDispatcher = {
		// Auth
		case Message("logout", _) => handleLogout()

		// Default
		case _ => MessageFailure("UNAVAILABLE")
	}

	override def postStop(): Unit = {
		Logger.debug(s"Socket close: $remoteAddr-$id")
		if (socket.isDefined) {
			socket.get.detach()
		}
	}
}
