package gtp3

import akka.actor._
import boopickle.DefaultBasic._
import boopickle.Pickler
import gtp3.Channel._
import gtp3.ChannelHandler._
import java.nio.ByteBuffer
import models.DB
import org.apache.commons.lang3.exception.ExceptionUtils
import reactive._
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure => TFailure, Success => TSuccess}
import slick.dbio.{DBIOAction, NoStream}

/**
  * A ChannelValidator is responsible to resolve ChannelRequest by either
  * accepting it or by rejecting it.
  */
trait ChannelValidator {
	def open(request: ChannelRequest): Unit
}

/**
  * Utilities for the ChannelHandler class
  */
object ChannelHandler {
	/**
	  * A generic Future[Payload] resolved to the empty Payload.
	  * It is used by the Unit encoder to skip the overhead of creating
	  * a new Future multiple times for the exact same value.
	  */
	private val futureEmptyPayload = Future.successful[Payload](Payload.empty)

	/**
	  * Message sent to the the channel actor to request the construction
	  * and sending of a GTP3 message to the user.
	  */
	case class SendMessage(message: String, payload: Payload)

	/**
	  * Type-class constructing the required Future[Payload] from anything
	  * acceptable as a return type for a request handler.
	  */
	trait FuturePayloadBuilder[-T] {
		def build(o: T): Future[Payload]
	}

	/**
	  * Convert any T for which there is a PayloadBuilder[T] to a Future[Payload]
	  * by wrapping the converted value inside a resolved Future.
	  */
	implicit def FuturePayloadWrapper[T: PayloadBuilder] = new FuturePayloadBuilder[T] {
		def build(o: T): Future[Payload] = Future.successful(Payload(o))
	}

	/**
	  * Convert any Future[T] for which there is a PayloadBuilder[T] to a Future[Payload]
	  */
	implicit def FuturePayloadConverter[T: PayloadBuilder] = new FuturePayloadBuilder[Future[T]] {
		def build(f: Future[T]): Future[Payload] = f map { q => Payload(q) }
	}

	/**
	  * Convert any DBIOAction[T, _, _] for which there is a PayloadBuilder[T] to a Future[Payload]
	  * by first running the action on the database then converting the resulting Future[T]
	  */
	implicit def FuturePayloadDBIOAction[T: PayloadBuilder] = new FuturePayloadBuilder[DBIOAction[T, NoStream, Nothing]] {
		def build(a: DBIOAction[T, NoStream, Nothing]): Future[Payload] = DB.run(a) map { q => Payload(q) }
	}

	/**
	  * Convert Future[Payload] to Future[Payload].
	  * Allow the request handler to return the expected type.
	  */
	implicit val FuturePayloadIdentity = new FuturePayloadBuilder[Future[Payload]] {
		def build(f: Future[Payload]): Future[Payload] = f
	}

	/**
	  * Convert any Throwable to a failed Future[Payload] with the Throwable as failure.
	  */
	implicit val FuturePayloadThrowable = new FuturePayloadBuilder[Throwable] {
		def build(e: Throwable): Future[Payload] = Future.failed(e)
	}

	/**
	  * Convert Unit to an already resolved Future[Payload] containing the empty Payload
	  */
	implicit val FuturePayloadUnit = new FuturePayloadBuilder[Unit] {
		def build(u: Unit): Future[Payload] = futureEmptyPayload
	}
}

/**
  * Super-class of every GTP3 channel handler.
  * Implements everything about input decoding, output encoding,
  * management of the messages/requests protocol.
  */
trait ChannelHandler extends Actor with Stash with PayloadBuilder.ProductWrites {
	/**
	  * Implicitly convert T to Option[T] in the context of ChannelHandler.
	  * TODO: Why do we need that ?
	  */
	implicit def OptionBoxing[T](v: T): Option[T] = Option(v)

	/**
	  * Reference to the channel actor for sending outgoing messages.
	  */
	private var channel: ActorRef = context.system.deadLetters

	// Init and close handlers
	private var init_handler: () => Unit = null
	private var stop_handler: () => Unit = null

	/**
	  * Define the init handler.
	  * The init handler will be called once this ChannelHandler is bound the outgoing channel actor
	  * and is ready to send messages.
	  */
	final def init(fn: => Unit) = init_handler = () => fn

	/**
	  * Define the stop handler.
	  * The stop handler will be called when the channel is closed.
	  */
	final def stop(fn: => Unit) = stop_handler = () => fn

	// Message-Request handlers
	private var handlers = Map[String, Payload => Future[Payload]]()

	/**
	  * Register a message handler.
	  *
	  * It is important to note that the return type of the handler is Unit because message
	  * cannot be directly responded to. Since the return type is actually ignored, no implicit
	  * conversion by FuturePayloadBuilder will be performed and in the case of DBIOAction, the
	  * action will not be executed automatically.
	  *
	  * As a matter a fact, both message() and request() handlers can be called by the client-side
	  * by using either the message or the request semantic. If there is no need for the return type
	  * to be ignored, it is better to declare every handlers as request().
	  */
	final def message(name: String)(fn: Payload => Unit): Unit = request(name)(fn)

	final def message2[T: Pickler](name: String)(fn: T => Unit): Unit = request2(name)(fn)

	/**
	  * Register a request handler.
	  * The request handler can return any type convertible to Payload by a PayloadBuilder
	  * Alternatively a Future of such a type, or a DBIOAction with a compatible result type
	  */
	final def request[T](name: String)(fn: Payload => T)(implicit fpb: FuturePayloadBuilder[T]): Unit = {
		handlers += name -> (fn andThen fpb.build)
	}
	/**
	  * Register a request handler.
	  * The request handler can return any type that is Pickleable
	  * Alternatively a Future of such a type, or a DBIOAction with a compatible result type
	  */
	final def request2[T: Pickler, R](name: String)(fn: T => R)(implicit p: Pickleable[R]): Unit = {
		handlers += name -> ((payload: Payload) => {
			val value = Unpickle[T].fromBytes(ByteBuffer.wrap(payload.buffer))
			p.pickelPayload(fn(value))
		})
	}

	// Akka message handler
	private var akka_handler: Receive = PartialFunction.empty

	/**
	  * Define the akka message handler.
	  * This must be a PartialFunction[Any, Unit] responsible to handle messages to which
	  * the channel has subscribed. Note that this handler is only called after the bound()
	  * receiver if it did not handler a specific message.
	  */
	final def akka(pf: Receive) = akka_handler = pf

	/**
	  * Send a message to the client.
	  * The data can be any T for which there exists a PayloadBuilder[T].
	  */
	def send[T: PayloadBuilder](msg: String, data: T): Unit = channel ! SendMessage(msg, Payload(data))
	final def send[T: PayloadBuilder](msg: String): Unit = send(msg, Payload.empty)

	/**
	  * Initial message receiver.
	  * Wait for the first Init message and stash everything else. Once the Init message
	  * is received, this function is permanently replaced by the bound receiver.
	  */
	final def receive = {
		case Init(c) =>
			channel = c
			unstashAll()
			context.become(bound orElse akka_handler, false)
			if (init_handler != null)
				init_handler()

		case _ => stash()
	}

	/**
	  * Message receiver once the Init message is received
	  */
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

	/**
	  * Handle a request
	  */
	def handle_request(req: String, payload: Payload): Future[Payload] = try {
		handlers.get(req) match {
			case Some(handler) => handler(payload)
			case None => Future.failed(new Exception(s"Undefined handler ($req)"))
		}
	} catch {
		case e: Throwable => Future.failed(e)
	}

	/**
	  * Close the channel
	  */
	def close(code: Int, reason: String) = channel ! Close(code, reason)

	/**
	  * Catch the actor death and call the stop handler if defined.
	  * Must be called by the Akka framework.
	  */
	override def postStop(): Unit = {
		if (stop_handler != null)
			stop_handler()
	}
}
