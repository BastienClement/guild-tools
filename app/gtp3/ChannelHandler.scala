package gtp3

import akka.actor._
import gtp3.ChannelHandler._
import org.apache.commons.lang3.exception.ExceptionUtils
import play.api.libs.json.JsNull
import reactive._
import Channel._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Success => TSuccess, Failure => TFailure}

trait ChannelValidator {
	def open(request: ChannelRequest): Unit
}

object ChannelHandler {
	// The generic empty-result future
	private val empty = Future.successful[Payload](JsNull)

	case class SendMessage(message: String, payload: Payload)
}

trait ChannelHandler extends Actor with Stash {
	// Implicitly converts to Future[Payload]
	implicit def ImplicitFuturePayload[T: PayloadBuilder](value: T): Future[Payload] = Future.successful(value)
	implicit def ImplicitFuturePayload[T: PayloadBuilder](future: Future[T]): Future[Payload] = future.map(Payload(_))

	// Implicitly converts to Option[T]
	implicit def ImplicitOption[T](value: T): Option[T] = Some(value)

	// Reference to the channel actor
	private var channel: ActorRef = context.system.deadLetters

	// Init and close handlers
	private var init_handler: () => Unit = null
	private var stop_handler: () => Unit = null

	def init(fn: => Unit) = init_handler = () => fn
	def stop(fn: => Unit) = stop_handler = () => fn

	// Request handlers
	type RequestHandler = (Payload) => Future[Payload]
	type MessageHandler = (Payload) => Unit

	private var handlers = Map[String, RequestHandler]()

	private def wrap(h: MessageHandler): RequestHandler = (p) => {
		h(p)
		ChannelHandler.empty
	}

	def message(name: String)(fn: MessageHandler) = request(name)(wrap(fn))
	def request(name: String)(fn: RequestHandler) = handlers += name -> fn

	// Akka message handler
	var akka_handler: Receive = PartialFunction.empty
	def akka(pf: Receive) = akka_handler = pf

	def send(msg: String, payload: Payload) = channel ! SendMessage(msg, payload)

	final def receive = {
		case Init(c) =>
			channel = c
			unstashAll()
			context.become(bound orElse akka_handler, false)
			if (init_handler != null)
				init_handler()

		case _ => stash()
	}

	final def bound: Receive = {
		case Message(msg, payload) =>
			handle_request(msg, payload) onFailure { case e =>
				// Catch Exception on message processing. Since there is no way to reply to a message, we
				// send a special $error message back.
				channel ! Message("$error", ExceptionUtils.getStackTrace(e))
			}

		case Request(rid, req, payload) =>
			handle_request(req, payload) onComplete {
				case TSuccess(payload) => channel ! Success(rid, payload)
				case TFailure(fail) => channel ! Failure(rid, fail)
			}

		case sm: SendMessage =>
			channel ! sm

		case Close(code, reason) =>
			self ! PoisonPill
	}

	// Handle a request
	def handle_request(req: String, payload: Payload): Future[Payload] = try {
		handlers.get(req) match {
			case Some(handler) => handler(payload)
			case None => Future.failed(new Exception(s"Undefined handler ($req)"))
		}
	} catch {
		case e: Throwable => Future.failed(e)
	}

	def close(code: Int, reason: String) = channel ! Close(code, reason)

	override def postStop(): Unit = {
		if (stop_handler != null)
			stop_handler()
	}
}
