package gtp3

import java.nio.charset.StandardCharsets
import play.api.libs.json.{JsNull, JsValue, Json, Writes}
import scodec.bits.ByteVector

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

// High priority builders
object PayloadBuilder extends PayloadBuilderMiddlePriority {
	// Used as context-bound implicit when the user passes an already built payload
	// This allow
	//   def foo[T: PayloadBuilder](o: T)
	// to accept payload object without specialization
	implicit val BuilderIndentity = new BufferStep[Payload] {
		def build(payload: Payload): Payload = payload
	}

	// Encode byte array to binary payload
	implicit val BuilderBuffer = new BufferStep[Array[Byte]] {
		def build(buf: Array[Byte]): Payload = buffer(buf)
	}

	// Encode string to string payload
	implicit val BuilderString = new StringStep[String] {
		def build(str: String): Payload = string(str)
	}

	// Encode JsNull specifically as the empty payload
	// Overloads the JsValue encoder that would have encoded "null" as a JSON string
	implicit val BuilderJsNull = new PayloadBuilder[JsNull.type] {
		def build(jsNull: JsNull.type): Payload = Payload.empty
	}

	// Encode Unit as the empty payload
	implicit val BuilderUnit = new PayloadBuilder[Unit] {
		def build(jsNull: Unit): Payload = Payload.empty
	}
}

// Intermediate priority builders
trait PayloadBuilderMiddlePriority extends PayloadBuilderLowPriority {
	// JsValue are encoded to JSON payload
	implicit val BuilderJsValue = new JsonStep[JsValue] {
		def build(js: JsValue): Payload = json(js)
	}
}

// Low priority builders
trait PayloadBuilderLowPriority extends PayloadBuilderSteps {
	// Any type T with a corresponding Write[T] is encoded: T -> JsValue -> JSON -> Payload
	// This is a low priority builder to allow more specific encoding first
	implicit def BuilderWrites[T: Writes] = new JsonStep[T] {
		def build(value: T): Payload = json(Json.toJson(value))
	}

	// Encode (A, B) with a Writes[T] available for every element as a JS array
	implicit def BuilderProduct2[A: Writes, B: Writes] = new JsonStep[(A, B)] {
		def build(prod: (A, B)): Payload = json(Json.arr(prod._1, prod._2))
	}

	// Encode (A, B, C) with a Writes[T] available for every element as a JS array
	implicit def BuilderProduct3[A: Writes, B: Writes, C: Writes] = new JsonStep[(A, B, C)] {
		def build(prod: (A, B, C)): Payload = json(Json.arr(prod._1, prod._2, prod._3))
	}

	// Encode (A, B, C, D) with a Writes[T] available for every element as a JS array
	implicit def BuilderProduct4[A: Writes, B: Writes, C: Writes, D: Writes] = new JsonStep[(A, B, C, D)] {
		def build(prod: (A, B, C, D)): Payload = json(Json.arr(prod._1, prod._2, prod._3, prod._4))
	}

	// Encode (A, B, C, D, E) with a Writes[T] available for every element as a JS array
	implicit def BuilderProduct5[A: Writes, B: Writes, C: Writes, D: Writes, E: Writes] = new JsonStep[(A, B, C, D, E)] {
		def build(prod: (A, B, C, D, E)): Payload = json(Json.arr(prod._1, prod._2, prod._3, prod._4, prod._5))
	}
}
