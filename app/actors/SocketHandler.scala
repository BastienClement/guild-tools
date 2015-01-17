package actors

import java.io.ByteArrayOutputStream
import java.util
import java.util.zip.{Deflater, Inflater}
import scala.annotation.switch
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

	// Message handler type
	type MessageDispatcher = Map[String, (JsValue) => MessageResponse]

	// Dispatch unauthenticated calls
	val unauthenticatedDispatcher: MessageDispatcher = Map(
		"auth" -> Auth.handleAuth,
		"auth:prepare" -> Auth.handlePrepare,
		"auth:login" -> Auth.handleLogin
	)

	// Dispatch authenticated calls
	val authenticatedDispatcher: MessageDispatcher = Map(
		"events:unbind" -> handleEventUnbind,

		"dashboard:load" -> Dashboard.handleLoad,

		"calendar:load" -> Calendar.handleLoad,
		"calendar:create" -> Calendar.handleCreate,
		"calendar:answer" -> Calendar.handleAnswer,
		"calendar:delete" -> Calendar.handleDelete,
		"calendar:event" -> Calendar.handleEvent,
		"calendar:event:invite" -> Calendar.handleEventInvite,
		"calendar:event:state" -> Calendar.handleEventState,
		"calendar:event:editdesc" -> Calendar.handleEventEditDesc,
		"calendar:event:promote" -> Calendar.handleEventPromote(true),
		"calendar:event:demote" -> Calendar.handleEventPromote(false),
		"calendar:event:kick" -> Calendar.handleEventKick,
		"calendar:comp:set" -> Calendar.handleCompSet(false),
		"calendar:comp:reset" -> Calendar.handleCompSet(true),
		"calendar:tab:create" -> Calendar.handleTabCreate,
		"calendar:tab:delete" -> Calendar.handleTabDelete,
		"calendar:tab:swap" -> Calendar.handleTabSwap,
		"calendar:tab:rename" -> Calendar.handleTabRename,
		"calendar:tab:wipe" -> Calendar.handleTabWipe,
		"calendar:tab:edit" -> Calendar.handleTabEdit,
		"calendar:tab:lock" -> Calendar.handleTabLock(true),
		"calendar:tab:unlock" -> Calendar.handleTabLock(false),
		"calendar:lock:status" -> Calendar.handleLockStatus,
		"calendar:lock:acquire" -> Calendar.handleLockAcquire,
		"calendar:lock:refresh" -> Calendar.handleLockRefresh,
		"calendar:lock:release" -> Calendar.handleLockRelease,
		"calendar:upcoming:events" -> Calendar.handleUpcomingEvents,

		"profile:load" -> Profile.handleLoad,
		"profile:enable" -> Profile.handleEnable(true),
		"profile:disable" -> Profile.handleEnable(false),
		"profile:promote" -> Profile.handlePromote,
		"profile:remove" -> Profile.handleRemove,
		"profile:role" -> Profile.handleRole,
		"profile:check" -> Profile.handleCheck,
		"profile:register" -> Profile.handleRegister,
		"profile:refresh" -> Profile.handleRefresh,

		"absences:load" -> Absences.handleLoad,
		"absences:create" -> Absences.handleCreate,
		"absences:edit" -> Absences.handleEdit,
		"absences:cancel" -> Absences.handleCancel,

		"chat:sync" -> Chat.handleSync,
		"chat:shoutbox:send" -> Chat.handleShoutboxSend,

		"roster:load" -> Roster.handleLoad,
		"roster:user" -> Roster.handleUser,
		"roster:char" -> Roster.handleChar,

		"composer:load" -> Composer.handleLoad,
		"composer:lockout:create" -> Composer.handleLockoutCreate,
		"composer:lockout:rename" -> Composer.handleLockoutRename,
		"composer:lockout:delete" -> Composer.handleLockoutDelete,
		"composer:group:create" -> Composer.handleGroupCreate,
		"composer:group:rename" -> Composer.handleGroupRename,
		"composer:group:delete" -> Composer.handleGroupDelete,
		"composer:slot:set" -> Composer.handleSlotSet,
		"composer:slot:unset" -> Composer.handleSlotUnset,
		"composer:export:group" -> Composer.handleExportGroup,

		"auth:logout" -> Auth.handleLogout
	)

	// Dispatch calls once socket is closed
	val zombieDispatcher: MessageDispatcher = Map()

	// Current dispatcher
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
				val response = dispatcher.getOrElse(cmd, handleUnavailable _)(arg)
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
	// Do not compress frames smaller than that
	val compress_threshold = 250

	// Compress data
	def deflate(data: Array[Byte]): Array[Byte] = {
		if (data.length < compress_threshold) {
			val dummy = new Array[Byte](data.length + 1)
			System.arraycopy(data, 0, dummy, 1, data.length)
			dummy
		} else {
			val d = new Deflater()
			d.setInput(data)
			d.finish()

			val os = new ByteArrayOutputStream()
			os.write(0x01)

			val buf = new Array[Byte](1024)
			while (!d.finished()) {
				val count = d.deflate(buf)
				os.write(buf, 0, count)
			}

			os.close()
			d.end()

			os.toByteArray
		}
	}

	// Decompress data
	def inflate(data: Array[Byte]): Array[Byte] = {
		val comp_mode = data(0)
		val comp_data = util.Arrays.copyOfRange(data, 1, data.length)

		if (comp_mode == 0x00) {
			comp_data
		} else {
			val i = new Inflater()
			i.setInput(comp_data)

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
