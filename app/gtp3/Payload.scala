package gtp3

import java.nio.charset.StandardCharsets

import play.api.libs.json._
import scodec.bits.ByteVector

import scala.language.{higherKinds, implicitConversions}

object Payload {
	// Construct a payload from a received buffer and flags (incoming payload)
	def apply(buf: ByteVector, flags: Int) = new Payload(buf, flags)

	// Construct a payload built from server data (outgoing payload)
	private def construct(buf: ByteVector, flags: Int) = {
		// TODO: implement deflate
		new Payload(buf, flags)
	}

	// Wrap a string inside a payload by encoding it as UTF8
	def apply(value: String) = construct(ByteVector(value.getBytes(StandardCharsets.UTF_8)), 0x02)

	// Wrap a JsValue inside a payload
	def apply(value: JsValue) = construct(ByteVector(Json.stringify(value).getBytes(StandardCharsets.UTF_8)), 0x06)

	// Wrap a buffer inside a payload
	def apply(buffer: Array[Byte]) = construct(ByteVector(buffer), 0x00)

	// Construct a payload from anything that can be used
	implicit def ImplicitPayload(value: JsValue): Payload = Payload(value)
	implicit def ImplicitPayload[T](value: T)(implicit w: Writes[T]): Payload = Payload(w.writes(value))
	implicit def ImplicitPayload(value: String): Payload = Payload(value)
	implicit def ImplicitPayload(value: Boolean): Payload = Payload(JsBoolean(value))

	// Construct a payload from any Iterable[T] with T convertible to JsValue
	implicit def ImplicitHigherKindPayload[T, U[_] <: Iterable[T]](value: U[T])(implicit w: Writes[T], iw: Writes[Iterable[JsValue]]): Payload = {
		Payload(iw.writes(value.map(w.writes)))
	}
}

class Payload(val byteVector: ByteVector, val flags: Int) {
	// Inflate a compressed buffer
	private def inflate(byteVector: Array[Byte]): Array[Byte] = ???

	// Direct access to raw byte data
	lazy val buffer: Array[Byte] =
		if ((flags & 0x01) != 0) inflate(byteVector.toArray)
		else byteVector.toArray

	// Simply convert the buffer data to String
	lazy val string: String =
		if ((flags & 0x02) != 0) new String(buffer, StandardCharsets.UTF_8)
		else throw new Exception("Attempt to read a binary frame as text")

	// Access complex JsValue from a JSON-encoded string
	lazy val value: JsValue =
		if ((flags & 0x04) != 0) Json.parse(buffer)
		else JsString(string) // Not JSON, fake a JsString

	def apply(selector: String) = value \ selector
	def apply[T](selector: (JsValue) => T) = selector(value)
}
