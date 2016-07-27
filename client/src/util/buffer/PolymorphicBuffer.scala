package util.buffer

import java.nio.ByteBuffer
import scala.scalajs.js.typedarray.ArrayBuffer
import scodec.bits.{BitVector, ByteVector}

sealed trait PolymorphicBuffer {
	protected type Buf
	protected val buffer: Buf
	protected val ops: BufferConv[Buf]

	@inline final def toByteArray: Array[Byte] = ops.toByteArray(buffer)
	@inline final def toBitVector: BitVector = ops.toBitVector(buffer)
	@inline final def toByteVector: ByteVector = ops.toByteVector(buffer)
	@inline final def toByteBuffer: ByteBuffer = ops.toByteBuffer(buffer)
	@inline final def toArrayBuffer: ArrayBuffer = ops.toArrayBuffer(buffer)
	@inline final def polymorphic: PolymorphicBuffer = this
}

private[buffer] class PolymorphicBufferImpl[B](val buffer: B)(implicit val ops: BufferConv[B])
		extends PolymorphicBuffer {type Buf = B }
