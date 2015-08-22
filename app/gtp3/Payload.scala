package gtp3

import java.nio.charset.StandardCharsets

import play.api.libs.json._
import scodec.bits.ByteVector

import scala.language.implicitConversions

object Payload {
	def apply(buf: ByteVector, flags: Int) = new Payload(buf, flags)

	def apply(value: String) = new Payload(ByteVector(value.getBytes(StandardCharsets.UTF_8)), 0x02)
	def apply(value: JsValue) = new Payload(ByteVector(Json.stringify(value).getBytes(StandardCharsets.UTF_8)), 0x06)
	def apply(buffer: Array[Byte]) = new Payload(ByteVector(buffer), 0x00)

	implicit def ImplicitPayload(value: JsValue): Payload = Payload(value)
	implicit def ImplicitPayload[T](value: T)(implicit w: Writes[T]): Payload = Payload(w.writes(value))
	implicit def ImplicitPayload(value: String): Payload = Payload(value)
	implicit def ImplicitPayload(value: Boolean): Payload = Payload(JsBoolean(value))
}

class Payload(val byteVector: ByteVector, val flags: Int) {
	lazy val inflated: Array[Byte] = {
		if ((flags & 0x01) == 0) {
			byteVector.toArray
		} else {
			???
		}
	}

	lazy val buffer: Array[Byte] = inflated

	lazy val value: JsValue = {
		if ((flags & 0x04) == 0) {
			JsString(new String(inflated, StandardCharsets.UTF_8))
		} else {
			Json.parse(inflated)
		}
	}

	lazy val string: String = new String(inflated, StandardCharsets.UTF_8)

	def get(selector: String) = value \ selector
	def get[T](selector: (JsValue) => T) = selector(value)
}
