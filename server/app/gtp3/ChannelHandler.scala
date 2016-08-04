package gtp3

import akka.actor._
import boopickle.DefaultBasic._
import boopickle.Pickler
import gtp3.Channel._
import gtp3.ChannelHandler._
import java.nio.ByteBuffer
import org.apache.commons.lang3.exception.ExceptionUtils
import reactive._
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure => TFailure, Success => TSuccess}

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
	  * Message sent to the the channel actor to request the construction
	  * and sending of a GTP3 message to the user.
	  */
	case class SendMessage(message: String, payload: Payload)

	@inline private[ChannelHandler] val wrapPayload = (p: Payload) => ByteBuffer.wrap(p.buffer)
	@inline private[ChannelHandler] def unpickle[T: Pickler] = Unpickle[T].fromBytes _
	@inline private[ChannelHandler] def pickle[T](implicit pickleable: Pickleable[T]) = pickleable.picklePayload _
}

/**
  * Super-class of every GTP3 channel handler.
  * Implements everything about input decoding, output encoding,
  * management of the messages/requests protocol.
  */
trait ChannelHandler extends Actor with Stash {
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
	final def message(name: String) = new {
		def apply(fn: => Unit): Unit = defineHandler(name, (_: Unit) => fn)
		def apply[T1: Pickler](fn: T1 => Unit): Unit = defineHandler(name, fn)
		def apply[T1: Pickler, T2: Pickler](fn: (T1, T2) => Unit): Unit = defineHandler(name, fn.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler](fn: (T1, T2, T3) => Unit): Unit = defineHandler(name, fn.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler](fn: (T1, T2, T3, T4) => Unit): Unit = defineHandler(name, fn.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, T5: Pickler](fn: (T1, T2, T3, T4, T5) => Unit): Unit = defineHandler(name, fn.tupled)
	}

	/**
	  * Register a request handler.
	  * The request handler can return any type convertible to Payload by a PayloadBuilder
	  * Alternatively a Future of such a type, or a DBIOAction with a compatible result type
	  */
	final def request(name: String) = new {
		def apply[R: Pickleable](fn: => R): Unit = defineHandler(name, (_: Unit) => fn)
		def apply[T1: Pickler, R: Pickleable](fn: T1 => R): Unit = defineHandler(name, fn)
		def apply[T1: Pickler, T2: Pickler, R: Pickleable](fn: (T1, T2) => R): Unit = defineHandler(name, fn.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, R: Pickleable](fn: (T1, T2, T3) => R): Unit = defineHandler(name, fn.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, R: Pickleable](fn: (T1, T2, T3, T4) => R): Unit = defineHandler(name, fn.tupled)
		def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, T5: Pickler, R: Pickleable](fn: (T1, T2, T3, T4, T5) => R): Unit = defineHandler(name, fn.tupled)
	}

	/** Registers a message handler */
	private def defineHandler[T: Pickler, R: Pickleable](name: String, fn: T => R): Unit = {
		handlers += name -> (wrapPayload andThen unpickle[T] andThen fn andThen pickle[R])
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
	final def send[T: Pickleable](msg: String, data: T): Unit = {
		for (payload <- implicitly[Pickleable[T]].picklePayload(data)) channel ! SendMessage(msg, payload)
	}

	@inline final def send[T: Pickleable](msg: String): Unit = send(msg, ())

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
				for (payload <- Payload.of(ExceptionUtils.getStackTrace(e))) channel ! SendMessage("$error", payload)
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
