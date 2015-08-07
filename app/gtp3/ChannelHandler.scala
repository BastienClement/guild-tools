package gtp3

import play.api.libs.json.{JsString, JsBoolean, JsValue}
import play.api.libs.json.Json.JsValueWrapper

import scala.concurrent.Future
import scala.language.implicitConversions
import reactive._

trait ChannelAcceptor {
	def open(request: ChannelRequest): Unit
}

trait ChannelHandler {
	var socket: Socket = null
	var channel: Channel = null

	implicit def ImplicitPayload(value: JsValue): Payload = Payload(value)
	implicit def ImplicitPayload(value: String): Payload = Payload(JsString(value))
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
				case _ =>  Future.failed(new Exception("Invalid result type"))
			}
			case None => Future.failed(new Exception("Undefined handler"))
		}
	}

	def message(msg: String, payload: Payload): Unit = {
		request(msg, payload)
	}
}
