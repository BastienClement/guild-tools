package gtp3

import java.nio._

abstract class DataParser[T] {
	var then: Option[DataParser] = None

	def ~(dp: DataParser): DataParser = {
		then = Some(dp)
		this
	}

	def apply(data: ByteBuffer) = {
		read(data) :: then.map(dp => read(data)).getOrElse(Nil)
	}

	def read[T](data: ByteBuffer): T
}

object UInt8 extends DataParser {
	def read
}

object Frame {
	def apply(buffer: Array[Byte]) = {
		val data = ByteBuffer.wrap(buffer)
		val opcode = data.get()


	}

	val HelloWorld = UInt8 ~ Int32
}

trait Frame

case class HelloFrame(magic_number: Int)
