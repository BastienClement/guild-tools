package gtp3

import scala.collection.mutable

object BufferPool {
	final val defaultLimit = 512
	final val defaultSize = 65535
}

/**
  * Buffer pool manager
  * Allocate 64 kB buffers used during compression/decompression of WebSocket data
  */
class BufferPool(val limit: Int = BufferPool.defaultLimit, val size: Int = BufferPool.defaultSize) {
	type Buffer = Array[Byte]

	private val buffers = mutable.Stack[Buffer]()
	private var constructed = 0

	/**
	  * Returns the number of constructed buffer in the pool.
	  */
	def count = constructed

	/**
	  * Returns the number of buffer currently available.
	  */
	def available = buffers.length

	/**
	  * Allocate a new buffer.
	  * Will never allocate more than 512 buffers
	  */
	private def newBuffer = {
		if (constructed >= limit) throw new TooManyBuffersException
		else constructed += 1
		new Array[Byte](size)
	}

	/**
	  * Attempt to get a pooled Buffer.
	  * Allocate a new one if the pool is empty.
	  */
	private def getBuffer = if (buffers.isEmpty) newBuffer else buffers.pop()

	/**
	  * Execute a function in a Buffer context.
	  * The allocated buffer will automatically be released when the function returns.
	  */
	def withBuffer[T](fn: Buffer => T): T = {
		val buf = synchronized { getBuffer }
		try {
			fn(buf)
		} finally {
			synchronized { buffers.push(buf) }
		}
	}
}
