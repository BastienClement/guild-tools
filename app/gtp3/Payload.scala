package gtp3

import java.nio.charset.StandardCharsets

import play.api.libs.json.{JsString, JsValue, Json}
import scodec.bits.ByteVector

import scala.language.implicitConversions

object Payload {
	def apply(buf: ByteVector, flags: Int) = new Payload(buf, flags)

	def apply(value: JsValue) = new Payload(Json.stringify(value), 0x06)
	def apply(buffer: Array[Byte]) = new Payload(buffer, 0x00)

	implicit def StringToByteVector(string: String): ByteVector = string.getBytes(StandardCharsets.UTF_8)
	implicit def ByteArrayToByteVector(arr: Array[Byte]): ByteVector = ByteVector(arr)
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
}
