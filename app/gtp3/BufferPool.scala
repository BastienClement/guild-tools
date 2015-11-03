package gtp3

/**
  * Buffer pool manager
  * Allocate 64 kB buffers used during compression/decompression of WebSocket data
  */
object BufferPool {
	type Buffer = Array[Byte]

	private var buffers: List[Buffer] = Nil
	private var constructed = 0

	/**
	  * Allocate a new buffer.
	  * Will never allocate more than 512 buffers
	  */
	private def newBuffer = {
		if (constructed > 512) throw new Exception("More than 512 buffer constructed, something is leaky!")
		else constructed += 1
		new Array[Byte](65535)
	}

	/**
	  * Attempt to get a pooled Buffer.
	  * Allocate a new one if the pool is empty.
	  */
	private def getBuffer = buffers match {
		case Nil =>
			newBuffer
		case b :: tail =>
			buffers = tail
			b
	}

	/**
	  * Execute a function in a Buffer context.
	  * The allocated buffer will automatically be released when the function returns.
	  */
	def withBuffer[T](fn: Buffer => T): T = {
		val buf = synchronized { getBuffer }
		try {
			fn(buf)
		} finally {
			synchronized { buffers = buf :: buffers }
		}
	}
}
