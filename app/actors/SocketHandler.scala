package actors

import scala.concurrent.duration._
import scala.util.{Failure, Success}
import akka.actor.{Actor, ActorRef, PoisonPill, actorRef2Scala}
import akka.util.Timeout
import api._
import gt.Global.ExecutionContext
import gt.{Global}
import models._
import actors.Actors._
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
	type MessageDispatcher = (String) => (JsValue) => MessageResponse
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

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
				val response = dispatcher(cmd)(arg)
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
			//Logger.debug(s"<<< ${msg.toString}")
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
	* Handle unavailable calls
	*/
	def handleUnavailable(arg: JsValue): MessageResponse = {
		MessageFailure("UNAVAILABLE")
	}

	/**
	 * Dispatch unauthenticated calls
	 */
	def unauthenticatedDispatcher: MessageDispatcher = {
		case "auth" => Auth.handleAuth
		case "auth:prepare" => Auth.handlePrepare
		case "auth:login" => Auth.handleLogin

		case _ => handleUnavailable
	}

	/**
	 * Dispatch authenticated calls
	 */
	def authenticatedDispatcher: MessageDispatcher = {
		case "events:unbind" => handleEventUnbind

		case "dashboard:load" => Dashboard.handleLoad

		case "calendar:load" => Calendar.handleLoad
		case "calendar:create" => Calendar.handleCreate
		case "calendar:answer" => Calendar.handleAnswer
		case "calendar:delete" => Calendar.handleDelete
		case "calendar:event" => Calendar.handleEvent
		case "calendar:event:invite" => Calendar.handleEventInvite
		case "calendar:event:state" => Calendar.handleEventState
		case "calendar:event:editdesc" => Calendar.handleEventEditDesc
		case "calendar:event:promote" => Calendar.handleEventPromote(true)
		case "calendar:event:demote" => Calendar.handleEventPromote(false)
		case "calendar:event:kick" => Calendar.handleEventKick
		case "calendar:comp:set" => Calendar.handleCompSet(false)
		case "calendar:comp:reset" => Calendar.handleCompSet(true)
		case "calendar:tab:create" => Calendar.handleTabCreate
		case "calendar:tab:delete" => Calendar.handleTabDelete
		case "calendar:tab:swap" => Calendar.handleTabSwap
		case "calendar:tab:rename" => Calendar.handleTabRename
		case "calendar:tab:wipe" => Calendar.handleTabWipe
		case "calendar:tab:edit" => Calendar.handleTabEdit
		case "calendar:tab:lock" => Calendar.handleTabLock(true)
		case "calendar:tab:unlock" => Calendar.handleTabLock(false)
		case "calendar:lock:status" => Calendar.handleLockStatus
		case "calendar:lock:acquire" => Calendar.handleLockAcquire
		case "calendar:lock:refresh" => Calendar.handleLockRefresh
		case "calendar:lock:release" => Calendar.handleLockRelease

		case "profile:load" => Profile.handleLoad
		case "profile:enable" => Profile.handleEnable(true)
		case "profile:disable" => Profile.handleEnable(false)
		case "profile:promote" => Profile.handlePromote
		case "profile:remove" => Profile.handleRemove
		case "profile:role" => Profile.handleRole
		case "profile:check" => Profile.handleCheck
		case "profile:register" => Profile.handleRegister

		case "absences:load" => Absences.handleLoad
		case "absences:create" => Absences.handleCreate
		case "absences:edit" => Absences.handleEdit
		case "absences:cancel" => Absences.handleCancel

		case "chat:onlines" => Chat.handleOnlines

		case "roster:load" => Roster.handleLoad
		case "roster:user" => Roster.handleUser
		case "roster:char" => Roster.handleChar

		case "auth:logout" => Auth.handleLogout
		case _ => handleUnavailable
	}

	/**
	 * $:events:unbind
	 */
	def handleEventUnbind(arg: JsValue): MessageResponse = {
		socket.unbindEvents()
		MessageSuccess
	}

	/**
	 * Websocket is now closed
	 */
	override def postStop(): Unit = {
		Logger.debug(s"Socket close: $remoteAddr-$id")
		SessionManager
	}
}
