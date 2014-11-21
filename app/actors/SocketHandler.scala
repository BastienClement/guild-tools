package actors

import scala.util.{Failure, Success}
import actors.Actors._
import akka.actor.{Actor, ActorRef, PoisonPill, actorRef2Scala}
import api._
import gt.Global
import gt.Global.ExecutionContext
import models._
import play.api.Logger
import play.api.libs.json._
import utils.EventFilter

class SocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor
with EventFilter
with AuthHandler
with ChatHandler
with RosterHandler
with DashboardHandler
with ProfileHandler
with CalendarHandler
with AbsencesHandler {
	// Protocol banner
	out ! Json.obj(
		"service" -> "GuildTools",
		"protocol" -> "GTP2",
		"version" -> "5.0",
		"rev" -> Global.serverVersion)

	// Current message handler
	type MessageDispatcher = (String) => (JsValue) => MessageResponse
	var dispatcher: MessageDispatcher = unauthenticatedDispatcher

	// Reference to the socket owner
	var user: User = null

	// The socket used session
	var session: Option[String] = None

	// Register with EventDispatcher
	Dispatcher.register(self)

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
		case event: Dispatchable =>
			onEvent(event)
	}

	/**
	 * Send the message object according to the result of the call
	 */
	def responseResult(id: JsValue, m: MessageResponse): Unit = {
		// Client not interested in the result anyway
		if (id == JsNull) return

		m match {
			// Simple success acknowledgement
			case MessageSuccess => {
				out ! Json.obj("$" -> "ack", "#" -> id, "&" -> JsNull)
			}

			// Results data
			case MessageResults(res) => {
				out ! Json.obj("$" -> "res", "#" -> id, "&" -> res)
			}

			// Failure
			case MessageFailure(message) => {
				out ! Json.obj("$" -> "nok", "#" -> id, "&" -> message)
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
	 * Handle filtered events
	 */
	def onEventFiltered(e: Dispatchable): Unit = e match {
		case event: Event => self ! Message("event", event.asJson)
		case _ => // Internal event are not to be send to the client
	}

	/**
	* Handle unavailable calls
	*/
	def handleUnavailable(arg: JsValue): MessageResponse = {
		MessageFailure("This feature is not available at the moment")
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
	 * Dispatch calls once socket is closed
	 */
	def zombieDispatcher: MessageDispatcher = {
		case _ => handleUnavailable
	}

	/**
	 * $:events:unbind
	 */
	def handleEventUnbind(arg: JsValue): MessageResponse = {
		unbindEvents()
		MessageSuccess
	}

	/**
	 * Websocket is now closed
	 */
	override def postStop(): Unit = {
		Dispatcher.unregister(self)
		Chat.disconnect(self)
	}
}
