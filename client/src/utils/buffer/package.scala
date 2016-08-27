package utils

import java.nio.ByteBuffer
import scala.language.implicitConversions
import scala.scalajs.js.typedarray.ArrayBuffer
import scodec.bits.{BitVector, ByteVector}

package object buffer {
	implicit class BufferOps[B](private val buffer: B) extends AnyVal {
		@inline def toByteArray(implicit ops: BufferConv[B]): Array[Byte] = ops.toByteArray(buffer)
		@inline def toBitVector(implicit ops: BufferConv[B]): BitVector = ops.toBitVector(buffer)
		@inline def toByteVector(implicit ops: BufferConv[B]): ByteVector = ops.toByteVector(buffer)
		@inline def toByteBuffer(implicit ops: BufferConv[B]): ByteBuffer = ops.toByteBuffer(buffer)
		@inline def toArrayBuffer(implicit ops: BufferConv[B]): ArrayBuffer = ops.toArrayBuffer(buffer)
		@inline def polymorphic(implicit ops: BufferConv[B]): PolymorphicBuffer = new PolymorphicBufferImpl(buffer)
	}

	@inline implicit def toPolymorphicBuffer[B: BufferConv](buffer: B): PolymorphicBuffer = {
		new PolymorphicBufferImpl(buffer)
	}
}
