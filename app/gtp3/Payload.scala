package gtp3

import java.nio.charset.StandardCharsets

import play.api.libs.json._
import scodec.bits.ByteVector

import scala.language.{higherKinds, implicitConversions}

object Payload {
	// Construct a Payload from a received buffer and flags (incoming payloads)
	def apply(buf: ByteVector, flags: Int) = new Payload(buf, flags)

	// Construct a Payload from anything that have a corresponding PayloadBuilder (outgoing payloads)
	def apply[T](value: T)(implicit builder: PayloadBuilder[T]): Payload = builder.build(value)

	// Empty dummy payload
	val empty = Payload(ByteVector.empty, 0x80)
}

class Payload(val byteVector: ByteVector, val flags: Int) {
	// Inflate a compressed buffer
	// TODO
	private def inflate(byteVector: Array[Byte]): Array[Byte] = ???

	// Flags
	final val compressed = (flags & 0x01) != 0
	final val utf8_data = (flags & 0x02) != 0
	final val json_data = (flags & 0x04) != 0
	final val ignore = (flags & 0x80) != 0

	// Direct access to raw byte data
	lazy val buffer: Array[Byte] =
		if (ignore) Array.empty[Byte]
		else if (compressed) inflate(byteVector.toArray)
		else byteVector.toArray

	// Simply convert the buffer data to String
	lazy val string: String =
		if (ignore) ""
		else if (utf8_data) new String(buffer, StandardCharsets.UTF_8)
		else throw new Exception("Attempt to read a binary frame as text")

	// Access complex JsValue from a JSON-encoded string
	lazy val value: JsValue =
		if (ignore) JsNull
		else if (json_data) Json.parse(buffer)
		else JsString(string) // Not JSON, fake a JsString

	// Access sub-properties of a JS object
	def apply(selector: String) = value \ selector

	// Access items of a JS array
	def apply(idx: Int) = value(idx)

	// Convert the JS value to type T
	def as[T: Reads] = value.as[T]

	// Same but to Option[T]
	def asOpt[T: Reads] = value.asOpt[T]
}
