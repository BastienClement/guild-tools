package gtp3

object BufferPool {
	type Buffer = Array[Byte]

	private var buffers: List[Buffer] = Nil
	private var constructed = 0

	private def newBuffer = {
		if (constructed > 512) throw new Exception("More than 512 buffer constructed, something is leaky !")
		else constructed += 1
		new Array[Byte](65535)
	}

	private def getBuffer = buffers match {
		case Nil =>
			newBuffer
		case b :: tail =>
			buffers = tail
			b
	}

	def withBuffer[T](fn: Buffer => T): T = {
		val buf = synchronized { getBuffer }
		try {
			fn(buf)
		} finally {
			synchronized { buffers = buf :: buffers }
		}
	}
}
