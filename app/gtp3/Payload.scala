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
	implicit def PayloadBuilderWrites[T: Writes] = new JsonStep[T] {
		def build(value: T): Payload = json(implicitly[Writes[T]].writes(value))
	}

	implicit def PayloadBuilderProduct2[A: Writes, B: Writes] = new JsonStep[(A, B)] {
		def build(prod: (A, B)): Payload = json(Json.arr(prod._1, prod._2))
	}

	implicit def PayloadBuilderProduct3[A: Writes, B: Writes, C: Writes] = new JsonStep[(A, B, C)] {
		def build(prod: (A, B, C)): Payload = json(Json.arr(prod._1, prod._2, prod._3))
	}

	implicit def PayloadBuilderProduct4[A: Writes, B: Writes, C: Writes, D:Writes] = new JsonStep[(A, B, C, D)] {
		def build(prod: (A, B, C, D)): Payload = json(Json.arr(prod._1, prod._2, prod._3, prod._4))
	}

	implicit def PayloadBuilderProduct4[A: Writes, B: Writes, C: Writes, D: Writes, E: Writes] = new JsonStep[(A, B, C, D, E)] {
		def build(prod: (A, B, C, D, E)): Payload = json(Json.arr(prod._1, prod._2, prod._3, prod._4, prod._5))
	}
}

trait PayloadBuilderMiddlePriority extends PayloadBuilderLowPriority {
	implicit object PayloadBuilderJsValue extends JsonStep[JsValue] {
		def build(js: JsValue): Payload = json(js)
	}
}

// High priority builders
object PayloadBuilder extends PayloadBuilderMiddlePriority {
	implicit object PayloadBuilderBuffer extends BufferStep[Array[Byte]] {
		def build(buf: Array[Byte]): Payload = buffer(buf)
	}

	implicit object PayloadBuilderString extends StringStep[String] {
		def build(str: String): Payload = string(str)
	}

	implicit object PayloadBuilderJsNull extends PayloadBuilder[JsNull.type] {
		def build(jsNull: JsNull.type): Payload = Payload.empty
	}
}
