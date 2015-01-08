package actors

import java.io.ByteArrayOutputStream
import java.util.zip.{Inflater, Deflater}
import scala.util.{Failure, Success}
import actors.Actors._
import akka.actor._
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
with AbsencesHandler
with ComposerHandler {
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
	implicit var user: User = null

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
		case "profile:refresh" => Profile.handleRefresh

		case "absences:load" => Absences.handleLoad
		case "absences:create" => Absences.handleCreate
		case "absences:edit" => Absences.handleEdit
		case "absences:cancel" => Absences.handleCancel

		case "chat:sync" => Chat.handleSync
		case "chat:shoutbox:send" => Chat.handleShoutboxSend

		case "roster:load" => Roster.handleLoad
		case "roster:user" => Roster.handleUser
		case "roster:char" => Roster.handleChar

		case "composer:load" => Composer.handleLoad
		case "composer:lockout:create" => Composer.handleLockoutCreate
		case "composer:lockout:delete" => Composer.handleLockoutDelete
		case "composer:group:create" => Composer.handleGroupCreate
		case "composer:group:delete" => Composer.handleGroupDelete
		case "composer:slot:set" => Composer.handleSlotSet
		case "composer:slot:unset" => Composer.handleSlotUnset

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
		ChatService.disconnect(self)
	}
}

/**
 * Wrap a socket handler to support compression
 */
class CompressedSocketHandler(val out: ActorRef, val remoteAddr: String) extends Actor {
	// Compress data
	def deflate(data: Array[Byte]): Array[Byte] = {
		val d = new Deflater()
		d.setInput(data)
		d.finish()

		val os = new ByteArrayOutputStream()
		val buf = new Array[Byte](1024)

		while (!d.finished()) {
			val count = d.deflate(buf)
			os.write(buf, 0, count)
		}

		os.close()
		d.end()

		os.toByteArray
	}

	// Decompress data
	def inflate(data: Array[Byte]): Array[Byte] = {
		val i = new Inflater()
		i.setInput(data)

		val os = new ByteArrayOutputStream()
		val buf = new Array[Byte](1024)

		while (!i.finished()) {
			val count = i.inflate(buf)
			os.write(buf, 0, count)
		}

		os.close()
		i.end()

		os.toByteArray
	}

	class OutputCompressor extends Actor {
		def receive = {
			case output: JsValue => out ! deflate(output.toString().getBytes("UTF-8"))
		}
	}

	val output_compressor = context.actorOf(Props(new OutputCompressor))
	val socket_handler = context.actorOf(Props(new SocketHandler(output_compressor, remoteAddr)))

	def receive = {
		case input: Array[Byte] => socket_handler ! Json.parse(inflate(input))
	}
}
