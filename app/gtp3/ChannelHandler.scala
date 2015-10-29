package gtp3

import akka.actor._
import gtp3.Channel._
import gtp3.ChannelHandler._
import org.apache.commons.lang3.exception.ExceptionUtils
import models.DB
import reactive._
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure => TFailure, Success => TSuccess}
import slick.dbio.{NoStream, DBIOAction}

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

	implicit def FuturePayloadDBIOAction[T: PayloadBuilder] = new FuturePayloadBuilder[DBIOAction[T, NoStream, Nothing]] {
		def build(a: DBIOAction[T, NoStream, Nothing]): Future[Payload] = DB.run(a) map { q => Payload(q) }
	}

	implicit val FuturePayloadIdentity = new FuturePayloadBuilder[Future[Payload]] {
		def build(f: Future[Payload]): Future[Payload] = f
	}

	implicit val FuturePayloadThrowable = new FuturePayloadBuilder[Throwable] {
		def build(e: Throwable): Future[Payload] = Future.failed(e)
	}

	implicit val FuturePayloadUnit = new FuturePayloadBuilder[Unit] {
		def build(u: Unit): Future[Payload] = Future.successful(Payload.empty)
	}
}

trait ChannelHandler extends Actor with Stash with PayloadBuilder.ProductWrites {
	// Implicitly converts to Option[T]
	implicit def OptionBoxing[T](v: T): Option[T] = Option(v)

	// Reference to the channel actor
	private var channel: ActorRef = context.system.deadLetters

	// Init and close handlers
	private var init_handler: () => Unit = null
	private var stop_handler: () => Unit = null

	final def init(fn: => Unit) = init_handler = () => fn
	final def stop(fn: => Unit) = stop_handler = () => fn

	// Request and message handlers
	// The request handler can return any type convertible to Payload by a PayloadBuilder
	// Alternatively a Future of such a type
	private var handlers = Map[String, Payload => Future[Payload]]()

	final def message(name: String)(fn: Payload => Unit): Unit = request(name)(fn)
	final def request[T](name: String)(fn: Payload => T)(implicit fpb: FuturePayloadBuilder[T]): Unit =
		handlers += name -> (fn andThen fpb.build)

	// Akka message handler
	private var akka_handler: Receive = PartialFunction.empty
	final def akka(pf: Receive) = akka_handler = pf

	// Output message
	def send[T: PayloadBuilder](msg: String, data: T): Unit = channel ! SendMessage(msg, Payload(data))
	final def send[T: PayloadBuilder](msg: String): Unit = send(msg, Payload.empty)

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
				channel ! SendMessage("$error", Payload(ExceptionUtils.getStackTrace(e)))
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
