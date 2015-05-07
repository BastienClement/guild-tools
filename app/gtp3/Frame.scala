package gtp3

import java.nio._

object Frame {
	def apply(buffer: Array[Byte]) = {
		val data = ByteBuffer.wrap(buffer)
		val opcode = data.get()


	}
}

trait Frame

case class HelloFrame(magic_number: Int)
