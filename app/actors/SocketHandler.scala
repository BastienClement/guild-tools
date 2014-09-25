package actors

import scala.concurrent.Future
import scala.util.{Failure, Success}

import play.api.libs.json.{JsNull, JsValue, Json}
import play.api.libs.json.Json.toJsFieldJsValueWrapper

import akka.actor.{Actor, ActorRef, PoisonPill, actorRef2Scala}
import api.{AuthHandler, Message, MessageFailed, MessageResponse, MessageResults, MessageSuccess}
import gt.Global.ExecutionContext
import gt.Socket

class SocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor with AuthHandler {
	// Protocol banner
	out ! Json.obj(
		"service" -> "GuildTools",
		"protocol" -> "GTP2",
		"version" -> "5.0"
	)

	// Current message handler
	type MessageDispatcher = PartialFunction[Message, MessageResponse]
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

	// Attached socket object
	var socket: Option[Socket] = None

	def receive = {
		// Incoming message
		case msg: JsValue =>
			val id = (msg \ "#")
			Future { dispatcher(Message((msg \ "$").as[String], (msg \ "&"))) } onComplete {
				_ match {
					case Success(message) => message match {
						case MessageSuccess() =>
							out ! Json.obj("$" -> "ack", "#" -> id, "&" -> JsNull)
						case MessageResults(res) =>
							out ! Json.obj("$" -> "res", "#" -> id, "&" -> res)
						case MessageFailed(err, message) =>
							out ! Json.obj(
								"$" -> "nok",
								"#" -> id,
								"&" -> Json.obj(
									"e" -> err,
									"m" -> message
								)
							)
					}

					case Failure(e) =>
						out ! Json.obj(
							"$" -> "err",
							"#" -> id,
							"&" -> Json.obj(
								"e" -> e.getClass().getName(),
								"m" -> e.getMessage()
							)
						)
						e.printStackTrace()
						self ! PoisonPill
				}
			}

		// Outgoing message
		case msg: Message =>
			out ! Json.obj("$" -> msg.cmd, "&" -> msg.arg, "#" -> JsNull)
	}

	def unauthenticatedDispatcher: MessageDispatcher = {
		case Message("auth", arg) => handleAuth(arg)
		case Message("login:prepare", arg) => handleLoginPrepare(arg)
		case Message("login:exec", arg) => handleLoginExec(arg)

		case _ =>
			MessageFailed(
				"Unavailable",
				"Requested operation is not available without prior authentication"
			)
	}

	def authenticatedDispatcher: MessageDispatcher = {
		case _ =>
			MessageFailed(
				"Unavailable",
				"Requested operation is not available"
			)
	}

	override def postStop(): Unit = {
		if (socket.isDefined) socket.get.detach()
	}
}
