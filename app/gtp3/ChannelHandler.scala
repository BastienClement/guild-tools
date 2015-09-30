package gtp3

import akka.actor._
import gtp3.Channel._
import gtp3.ChannelHandler._
import org.apache.commons.lang3.exception.ExceptionUtils
import reactive._

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure => TFailure, Success => TSuccess}

trait ChannelValidator {
	def open(request: ChannelRequest): Unit
}

object ChannelHandler {
	// The generic empty-result future
	private val empty = Future.successful[Payload](Payload.empty)

	// Outgoing message
	case class SendMessage(message: String, payload: Payload)

	trait FuturePayloadBuilder[-T] {
		def build(o: T): Future[Payload]
	}

	implicit def FuturePayloadWrapper[T: PayloadBuilder] = new FuturePayloadBuilder[T] {
		def build(o: T): Future[Payload] = Future.successful(Payload(o))
	}

	implicit def FuturePayloadConverter[T: PayloadBuilder] = new FuturePayloadBuilder[Future[T]] {
		def build(f: Future[T]): Future[Payload] = f map { q => Payload(q) }
	}
}

trait ChannelHandler extends Actor with Stash {
	// Implicitly converts to Option[T]
	implicit def ImplicitOption[T](value: T): Option[T] = Option(value)

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

	private def adapter[T](fn: (Payload) => T)(implicit fpb: FuturePayloadBuilder[T]): RequestHandler =
		(p: Payload) => fpb.build(fn(p))

	// Register a request handler
	// The handler can return any type convertible to Payload by a PayloadBuilder
	// Alternatively a Future of such a type
	def request[T: FuturePayloadBuilder](name: String)(fn: (Payload) => T) = handlers += name -> adapter(fn)

	// Register a message handler
	def message(name: String)(fn: MessageHandler) = request(name)(wrap(fn))

	// Akka message handler
	var akka_handler: Receive = PartialFunction.empty
	def akka(pf: Receive) = akka_handler = pf

	// Output message
	def send[T: PayloadBuilder](msg: String, data: T) = channel ! SendMessage(msg, Payload(data))

	// Initial message receiver
	// Wait for the first Init message and stash everything else
	final def receive = {
		case Init(c) =>
			channel = c
			unstashAll()
			context.become(bound orElse akka_handler, false)
			if (init_handler != null)
				init_handler()

		case _ => stash()
	}

	// Message receiver once the Init message is received
	final def bound: Receive = {
		// Incoming message
		case Message(msg, payload) =>
			handle_request(msg, payload) onFailure { case e =>
				// Catch Exception on message processing. Since there is no way to reply to a message, we
				// send a special $error message back.
				channel ! Message("$error", Payload(ExceptionUtils.getStackTrace(e)))
			}

		// Incoming request
		case Request(rid, req, payload) =>
			handle_request(req, payload) onComplete {
				case TFailure(fail) => channel ! Failure(rid, fail)
				case TSuccess(pyld) => channel ! Success(rid, pyld)
			}

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
