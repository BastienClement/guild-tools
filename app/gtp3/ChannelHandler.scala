package gtp3

import play.api.libs.json.{JsBoolean, JsValue}
import play.api.libs.json.Json.JsValueWrapper

import scala.concurrent.Future
import scala.language.implicitConversions
import reactive._

trait ChannelAcceptor {
	def open(request: ChannelRequest): Unit
}

trait ChannelHandler {
	implicit def JSValueToPayload(value: JsValue): Payload = Payload(value)
	implicit def BooleanToPayload(value: Boolean): Payload = Payload(JsBoolean(value))
	implicit def JSValueToFuturePayload(value: JsValue): Future[Payload] = Payload(value)
	implicit def BooleanToFuturePayload(value: Boolean): Future[Payload] = Payload(JsBoolean(value))
	implicit def FutureJSValueToFuturePayload(value: Future[JsValue]): Future[Payload] = value map { Payload(_) }
	implicit def FutureBooleanToFuturePayload(value: Future[Boolean]): Future[Payload] = value map { v => Payload(JsBoolean(v)) }

	type Handlers =  PartialFunction[String, (Payload) => Any]
	def handlers: Handlers

	def request(req: String, payload: Payload): Future[Payload] = {
		handlers.lift.apply(req) match {
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
