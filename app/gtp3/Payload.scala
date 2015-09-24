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

	// Implicitly convert anything with a PayloadBuilder to a Payload
	implicit def ImplicitPayload[T: PayloadBuilder](value: T): Payload = Payload(value)
}

class Payload(val byteVector: ByteVector, val flags: Int) {
	// Inflate a compressed buffer
	// TODO
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

	// Access sub-properties of a JS object
	def apply(selector: String) = value \ selector
	def apply[T](selector: (JsValue) => T) = selector(value)

	// Access items of a JS array
	def apply(idx: Int) = value(idx)

	// Convert the JS value to type T
	def as[T: Reads] = value.as[T]
	def asOpt[T: Reads] = value.asOpt[T]
}

// A PayloadBuilder construct a Payload from an object of type T
trait PayloadBuilder[-T] {
	def build(o: T): Payload
}

// Builder steps are responsible for translating data to ByteVector
trait PayloadBuilderSteps {
	// Compress and convert to ByteVector
	trait BufferStep[-T] extends PayloadBuilder[T] {
		def buffer(buf: Array[Byte], flags: Int = 0, compress: Boolean = true): Payload = {
			val bv = ByteVector(buf)
			new Payload(bv, flags)
		}
	}

	// Encode string as UTF-8
	trait StringStep[-T] extends BufferStep[T] {
		def string(str: String, flags: Int = 0): Payload = {
			val bytes = str.getBytes(StandardCharsets.UTF_8)
			buffer(bytes, flags | 0x02)
		}
	}

	// Stringify JsValue to JSON
	trait JsonStep[-T] extends StringStep[T] {
		def json(js: JsValue, flags: Int = 0): Payload = {
			string(Json.stringify(js), flags | 0x04)
		}
	}
}

// Low priority builders
trait PayloadBuilderLowPriority extends PayloadBuilderSteps {
	// Any type T with a corresponding Write[T] is encoded: T -> JsValue -> JSON -> Payload
	implicit def WritesBuilder[T: Writes] = new JsonStep[T] {
		def build(value: T): Payload = json(implicitly[Writes[T]].writes(value))
	}
}

// High priority builders
object PayloadBuilder extends PayloadBuilderLowPriority {
	implicit object BufferBuilder extends BufferStep[Array[Byte]] {
		def build(buf: Array[Byte]): Payload = buffer(buf)
	}

	implicit object StringBuilder extends StringStep[String] {
		def build(str: String): Payload = string(str)
	}

	implicit object JsValueBuilder extends JsonStep[JsValue] {
		def build(js: JsValue): Payload = json(js)
	}
}
