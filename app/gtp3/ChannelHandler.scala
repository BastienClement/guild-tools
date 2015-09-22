package gtp3

import play.api.libs.json.JsNull
import reactive._

import scala.annotation.StaticAnnotation
import scala.concurrent.Future
import scala.language.implicitConversions

trait ChannelValidator {
	def open(request: ChannelRequest): Unit
}

trait InitHandler {
	this: ChannelHandler =>
	def init()
}

trait CloseHandler {
	this: ChannelHandler =>
	def close()
}

trait ChannelHandler {
	// Socket and channel associated to this handler
	var socket: Socket = null
	var channel: Channel = null

	// Alias to the socket authenticated user
	def user = socket.user

	// Implicitly converts to Future[Payload]
	implicit def ImplicitFuturePayload[T](value: T)(implicit ev: T => Payload): Future[Payload] = Future.successful[Payload](value)
	implicit def ImplicitFuturePayload[T](future: Future[T])(implicit ev: T => Payload): Future[Payload] = future.map(ev(_))

	// The generic empty-result future
	private val empty = Future.successful[Payload](JsNull)

	// Request handlers
	type RequestHandler = (Payload) => Future[Payload]
	type MessageHandler = (Payload) => Unit

	private var handlers = Map[String, RequestHandler]()

	private def wrap(h: MessageHandler): RequestHandler = (p) => {
		h(p)
		empty
	}

	def message(name: String)(fn: MessageHandler) = request(name)(wrap(fn))
	def request(name: String)(fn: RequestHandler) = handlers += name -> fn

	// Handle a request
	def request(req: String, payload: Payload): Future[Payload] = {
		handlers.get(req) match {
			case Some(handler) => handler(payload) match {
				case p: Future[_] => p.asInstanceOf[Future[Payload]]
				case p: Payload => Future.successful(p)
				case _ => empty
			}
			case None => Future.failed(new Exception(s"Undefined handler ($req)"))
		}
	}

	// Handle a message (same as request but without result)
	def message(msg: String, payload: Payload): Unit = request(msg, payload)
}
