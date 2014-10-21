package actors

import akka.actor.{ Actor, ActorRef, PoisonPill, actorRef2Scala }
import api._
import gt.Global.ExecutionContext
import gt.{ Socket, User }
import play.api.Logger
import play.api.libs.json._
import scala.util.{ Failure, Success }

class SocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor
	with AuthHandler
	with ChatHandler
	with ProfileHandler
	with CalendarHandler {
	// Debug socket ID
	val id = utils.randomToken()

	// Protocol banner
	out ! Json.obj(
		"service" -> "GuildTools",
		"protocol" -> "GTP2",
		"version" -> "5.0")

	Logger.debug(s"Socket open: $remoteAddr-$id")

	// Current message handler
	type MessageDispatcher = (String, JsValue) => MessageResponse
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

	// Attached socket object
	var socket: Socket = null

	// Alias to the socket user
	var user: User = null

	/**
	 * Handle actor messages
	 */
	def receive = {
		// Incoming message
		case message: JsValue => {
			val id = message \ "#"
			Logger.debug(s">>> $message")

			try {
				val cmd = (message \ "$").as[String]
				val arg = (message \ "&")
				val response = dispatcher(cmd, arg)
				responseResult(id, response)
			} catch {
				case e: Throwable => responseError(id, e)
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
			Logger.debug(s"<<< ${msg.toString}")
			out ! msg
		}

		// Dispatching event
		case event: Event => {
			if (socket != null) {
				socket.handleEvent(event)
			}
		}
	}

	/**
	 * Send the message object according to the result of the call
	 */
	def responseResult(id: JsValue, m: MessageResponse): Unit = {
		// Client not interested in the result
		if (id == JsNull) return

		def outputJson(js: JsValue) = {
			Logger.debug(s"<<< ${js.toString}")
			out ! js
		}

		m match {
			// Simple success acknowledgement
			case MessageSuccess => {
				outputJson(Json.obj("$" -> "ack", "#" -> id, "&" -> JsNull))
			}

			// Results data
			case MessageResults(res) => {
				outputJson(Json.obj("$" -> "res", "#" -> id, "&" -> res))
			}

			// Soft-failure
			case MessageFailure(err, m) => {
				outputJson(Json.obj(
					"$" -> "nok",
					"#" -> id,
					"&" -> Json.obj(
						"e" -> err,
						"m" -> m)))
			}

			// Verbose failure
			case MessageAlert(alert) => {
				outputJson(Json.obj("$" -> "alert", "#" -> id, "&" -> alert))
			}

			// Response is not yet available
			case MessageDeferred(future) => {
				future onComplete {
					case Success(result) => responseResult(id, result)
					case Failure(e) => responseError(id, e)
				}
			}
		}
	}

	/**
	 * Handle critical failure due to user input
	 */
	def responseError(id: JsValue, e: Throwable): Unit = {
		out ! Json.obj(
			"$" -> "err",
			"#" -> id,
			"&" -> Json.obj(
				"e" -> e.getClass.getName,
				"m" -> e.getMessage))

		Logger.error("Fatal socket error", e)
		self ! PoisonPill
	}

	/**
	 * Dispatch unauthenticated calls
	 */
	def unauthenticatedDispatcher: MessageDispatcher = {
		case ("auth", arg) => handleAuth(arg)
		case ("auth:prepare", arg) => handleAuthPrepare(arg)
		case ("auth:login", arg) => handleAuthLogin(arg)

		case _ => MessageFailure("UNAVAILABLE")
	}

	/**
	 * Dispatch authenticated calls
	 */
	def authenticatedDispatcher: MessageDispatcher = {
		case ("events:unbind", _) => handleEventUnbind()

		case ("calendar:load", arg) => handleCalendarLoad(arg)
		case ("calendar:create", arg) => handleCalendarCreate(arg)
		case ("calendar:answer", arg) => handleCalendarAnswer(arg)
		case ("calendar:delete", arg) => handleCalendarDelete(arg)
		case ("calendar:event", arg) => handleCalendarEvent(arg)
		case ("calendar:event:state", arg) => handleCalendarEventState(arg)
		case ("calendar:event:editdesc", arg) => handleCalendarEventEditDesc(arg)
		case ("calendar:comp:set", arg) => handleCalendarCompSet(arg, false)
		case ("calendar:comp:reset", arg) => handleCalendarCompSet(arg, true)
		case ("calendar:tab:create", arg) => handleCalendarTabCreate(arg)
		case ("calendar:tab:delete", arg) => handleCalendarTabDelete(arg)
		case ("calendar:tab:swap", arg) => handleCalendarTabSwap(arg)
		case ("calendar:tab:rename", arg) => handleCalendarTabRename(arg)
		case ("calendar:tab:wipe", arg) => handleCalendarTabWipe(arg)
		case ("calendar:tab:edit", arg) => handleCalendarTabEdit(arg)
		case ("calendar:lock:status", arg) => handleCalendarLockStatus(arg)
		case ("calendar:lock:acquire", arg) => handleCalendarLockAcquire(arg)
		case ("calendar:lock:refresh", arg) => handleCalendarLockRefresh()
		case ("calendar:lock:release", arg) => handleCalendarLockRelease()

		case ("profile:load", arg) => handleProfileLoad(arg)
		case ("profile:enable", arg) => handleProfileEnable(arg, true)
		case ("profile:disable", arg) => handleProfileEnable(arg, false)
		case ("profile:promote", arg) => handleProfilePromote(arg)
		case ("profile:remove", arg) => handleProfileRemove(arg)
		case ("profile:role", arg) => handleProfileRole(arg)
		case ("profile:check", arg) => handleProfileCheck(arg)
		case ("profile:register", arg) => handleProfileRegister(arg)

		case ("chat:onlines", _) => handleChatOnlines()
		case ("auth:logout", _) => handleAuthLogout()
		case _ => MessageFailure("UNAVAILABLE")
	}

	/**
	 * $:events:unbind
	 */
	def handleEventUnbind(): MessageResponse = {
		socket.unbindEvents()
		MessageSuccess
	}

	/**
	 * Websocket is now closed
	 */
	override def postStop(): Unit = {
		Logger.debug(s"Socket close: $remoteAddr-$id")
		if (socket != null) {
			socket.detach()
		}
	}
}
