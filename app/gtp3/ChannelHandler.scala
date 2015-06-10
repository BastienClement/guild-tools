package gtp3

import play.api.libs.json.{JsBoolean, JsValue}
import play.api.libs.json.Json.JsValueWrapper

import scala.concurrent.Future
import scala.language.implicitConversions

trait ChannelAcceptor {
	def open(request: ChannelRequest): Unit
}

trait ChannelHandler {
	implicit def toFuture[V](value: V): Future[V] = Future.successful(value)
	implicit def toFuturePayload(value: JsValue): Future[Payload] = Payload(value)
	implicit def toFuturePayload(value: Boolean): Future[Payload] = JsBoolean(value)

	type Handlers =  PartialFunction[String, (Payload) => Any]
	def handlers: Handlers

	def request(req: String, payload: Payload): Future[Payload] = {
		handlers.lift.apply(req) match {
			case Some(handler) => handler(payload) match {
				case p: Future[Payload] => p
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
