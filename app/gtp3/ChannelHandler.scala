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

	def request(req: String, payload: Payload): Future[Payload]
}
