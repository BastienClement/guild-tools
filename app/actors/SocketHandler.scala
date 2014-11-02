package actors

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorRef, PoisonPill, actorRef2Scala}
import akka.util.Timeout
import api._
import gt.Global.ExecutionContext
import gt.{Global, Socket, User}
import play.api.Logger
import play.api.libs.json._

class SocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor
with AuthHandler
with ChatHandler
with RosterHandler
with DashboardHandler
with ProfileHandler
with CalendarHandler
with AbsencesHandler {
	// Debug socket ID
	val id = utils.randomToken()

	// Protocol banner
	out ! Json.obj(
		"service" -> "GuildTools",
		"protocol" -> "GTP2",
		"version" -> "5.0",
		"rev" -> Global.serverVersion)

	Logger.debug(s"Socket open: $remoteAddr-$id")

	// Current message handler
	type MessageDispatcher = (String, JsValue) => MessageResponse
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

	// Attached socket object
	var socket: Socket = null

	// Alias to the socket user
	var user: User = null

	// Timeout for ask pattern (Akka)
	implicit val timeout = Timeout(5.seconds)

	// Filter function signature
	type EventFilter = PartialFunction[Event, Boolean]

	/**
	 * Handle actor messages
	 */
	def receive = {
		// Incoming message
		case message: JsValue => {
			val id = message \ "#"
			//Logger.debug(s">>> $message")

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
			//Logger.debug(s"<<< ${js.toString}")
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
		case ("auth", arg) => Auth.handleAuth(arg)
		case ("auth:prepare", arg) => Auth.handlePrepare(arg)
		case ("auth:login", arg) => Auth.handleLogin(arg)

		case _ => MessageFailure("UNAVAILABLE")
	}

	/**
	 * Dispatch authenticated calls
	 */
	def authenticatedDispatcher: MessageDispatcher = {
		case ("events:unbind", _) => handleEventUnbind()

		case ("dashboard:load", _) => Dashboard.handleLoad()

		case ("calendar:load", arg) => Calendar.handleLoad(arg)
		case ("calendar:create", arg) => Calendar.handleCreate(arg)
		case ("calendar:answer", arg) => Calendar.handleAnswer(arg)
		case ("calendar:delete", arg) => Calendar.handleDelete(arg)
		case ("calendar:event", arg) => Calendar.handleEvent(arg)
		case ("calendar:event:state", arg) => Calendar.handleEventState(arg)
		case ("calendar:event:editdesc", arg) => Calendar.handleEventEditDesc(arg)
		case ("calendar:comp:set", arg) => Calendar.handleCompSet(arg, false)
		case ("calendar:comp:reset", arg) => Calendar.handleCompSet(arg, true)
		case ("calendar:tab:create", arg) => Calendar.handleTabCreate(arg)
		case ("calendar:tab:delete", arg) => Calendar.handleTabDelete(arg)
		case ("calendar:tab:swap", arg) => Calendar.handleTabSwap(arg)
		case ("calendar:tab:rename", arg) => Calendar.handleTabRename(arg)
		case ("calendar:tab:wipe", arg) => Calendar.handleTabWipe(arg)
		case ("calendar:tab:edit", arg) => Calendar.handleTabEdit(arg)
		case ("calendar:tab:lock", arg) => Calendar.handleTabLock(arg, true)
		case ("calendar:tab:unlock", arg) => Calendar.handleTabLock(arg, false)
		case ("calendar:lock:status", arg) => Calendar.handleLockStatus(arg)
		case ("calendar:lock:acquire", arg) => Calendar.handleLockAcquire(arg)
		case ("calendar:lock:refresh", arg) => Calendar.handleLockRefresh()
		case ("calendar:lock:release", arg) => Calendar.handleLockRelease()

		case ("profile:load", arg) => Profile.handleLoad(arg)
		case ("profile:enable", arg) => Profile.handleEnable(arg, true)
		case ("profile:disable", arg) => Profile.handleEnable(arg, false)
		case ("profile:promote", arg) => Profile.handlePromote(arg)
		case ("profile:remove", arg) => Profile.handleRemove(arg)
		case ("profile:role", arg) => Profile.handleRole(arg)
		case ("profile:check", arg) => Profile.handleCheck(arg)
		case ("profile:register", arg) => Profile.handleRegister(arg)

		case ("absences:load", _) => Absences.handleLoad()
		case ("absences:create", arg) => Absences.handleCreate(arg)
		case ("absences:edit", arg) => Absences.handleEdit(arg)
		case ("absences:cancel", arg) => Absences.handleCancel(arg)

		case ("chat:onlines", _) => Chat.handleOnlines()

		case ("roster:load", _) => Roster.handleLoad()
		case ("roster:user", arg) => Roster.handleUser(arg)
		case ("roster:char", arg) => Roster.handleChar(arg)

		case ("auth:logout", _) => Auth.handleLogout()
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
