package gtp3

import play.api.libs.json.{JsBoolean, JsValue, Writes}
import reactive._

import scala.concurrent.Future
import scala.language.implicitConversions

trait ChannelValidator {
	def open(request: ChannelRequest): Unit
}

trait InitHandler { this: ChannelHandler =>
	def init()
}

trait CloseHandler { this: ChannelHandler =>
	def close()
}

trait ChannelHandler {
	var socket: Socket = null
	var channel: Channel = null

	def user = socket.user

	implicit def ImplicitPayload(value: JsValue): Payload = Payload(value)
	implicit def ImplicitPayload[T](value: T)(implicit w: Writes[T]): Payload = Payload(w.writes(value))
	implicit def ImplicitPayload(value: String): Payload = Payload(value)
	implicit def ImplicitPayload(value: Boolean): Payload = Payload(JsBoolean(value))

	implicit def ImplicitFuturePayload[T](value: T)(implicit ev: T => Payload): Future[Payload] = Future.successful[Payload](value)
	implicit def ImplicitFuturePayload[T](future: Future[T])(implicit ev: T => Payload): Future[Payload] = future.map(ev(_))

	type Handler = (Payload) => Any
	type Handlers = Map[String, Handler]

	val handlers: Handlers

	def request(req: String, payload: Payload): Future[Payload] = {
		handlers.get(req) match {
			case Some(handler) => handler(payload) match {
				case p: Future[_] => p.asInstanceOf[Future[Payload]]
				case p: Payload => Future.successful(p)
				case _ => Future.failed(new Exception("Invalid result type"))
			}
			case None => Future.failed(new Exception("Undefined handler"))
		}
	}

	def message(msg: String, payload: Payload): Unit = {
		request(msg, payload)
	}
}
