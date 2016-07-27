package util.buffer

import java.nio.ByteBuffer
import scala.scalajs.js.typedarray.{ArrayBuffer, _}
import scodec.bits.{BitVector, ByteVector}

trait BufferConv[T] {
	def toByteArray(buffer: T): Array[Byte]
	def toBitVector(buffer: T): BitVector
	def toByteVector(buffer: T): ByteVector
	def toByteBuffer(buffer: T): ByteBuffer
	def toArrayBuffer(buffer: T): ArrayBuffer
}

object BufferConv {
	implicit val ByteArrayIsBuffer = new BufferConv[Array[Byte]] {
		@inline def toByteArray(buffer: Array[Byte]): Array[Byte] = buffer
		@inline def toBitVector(buffer: Array[Byte]): BitVector = BitVector.view(buffer)
		@inline def toByteVector(buffer: Array[Byte]): ByteVector = ByteVector.view(buffer)
		@inline def toByteBuffer(buffer: Array[Byte]): ByteBuffer = ByteBuffer.wrap(buffer)
		@inline def toArrayBuffer(buffer: Array[Byte]): ArrayBuffer = buffer.toTypedArray.buffer
	}

	implicit val BitVectorIsBuffer = new BufferConv[BitVector] {
		@inline def toByteArray(buffer: BitVector): Array[Byte] = buffer.toByteArray
		@inline def toBitVector(buffer: BitVector): BitVector = buffer
		@inline def toByteVector(buffer: BitVector): ByteVector = buffer.toByteVector
		@inline def toByteBuffer(buffer: BitVector): ByteBuffer = buffer.toByteBuffer
		@inline def toArrayBuffer(buffer: BitVector): ArrayBuffer = buffer.toByteArray.toTypedArray.buffer
	}

	implicit val ByteVectorIsBuffer = new BufferConv[ByteVector] {
		@inline def toByteArray(buffer: ByteVector): Array[Byte] = buffer.toArray
		@inline def toBitVector(buffer: ByteVector): BitVector = buffer.toBitVector
		@inline def toByteVector(buffer: ByteVector): ByteVector = buffer
		@inline def toByteBuffer(buffer: ByteVector): ByteBuffer = ByteBuffer.wrap(buffer.toArray)
		@inline def toArrayBuffer(buffer: ByteVector): ArrayBuffer = buffer.toArray.toTypedArray.buffer
	}

	implicit val ByteBufferIsBuffer = new BufferConv[ByteBuffer] {
		@inline def toByteArray(buffer: ByteBuffer): Array[Byte] = {
			val array = new Array[Byte](buffer.remaining)
			buffer.get(array)
			array
		}

		@inline def toBitVector(buffer: ByteBuffer): BitVector = BitVector.view(buffer)
		@inline def toByteVector(buffer: ByteBuffer): ByteVector = ByteVector.view(buffer)
		@inline def toByteBuffer(buffer: ByteBuffer): ByteBuffer = buffer
		@inline def toArrayBuffer(buffer: ByteBuffer): ArrayBuffer = TypedArrayBufferOps.byteBufferOps(buffer).arrayBuffer()
	}

	implicit val ArrayBufferIsBuffer = new BufferConv[ArrayBuffer] {
		@inline def toByteArray(buffer: ArrayBuffer): Array[Byte] = new Int8Array(buffer).toArray
		@inline def toBitVector(buffer: ArrayBuffer): BitVector = BitVector.view(toByteArray(buffer))
		@inline def toByteVector(buffer: ArrayBuffer): ByteVector = ByteVector.view(toByteArray(buffer))
		@inline def toByteBuffer(buffer: ArrayBuffer): ByteBuffer = TypedArrayBuffer.wrap(buffer)
		@inline def toArrayBuffer(buffer: ArrayBuffer): ArrayBuffer = buffer
	}

	implicit val PolymorphicBufferIsBuffer = new BufferConv[PolymorphicBuffer] {
		def toByteArray(buffer: PolymorphicBuffer): Array[Byte] = buffer.toByteArray
		def toBitVector(buffer: PolymorphicBuffer): BitVector = buffer.toBitVector
		def toByteVector(buffer: PolymorphicBuffer): ByteVector = buffer.toByteVector
		def toByteBuffer(buffer: PolymorphicBuffer): ByteBuffer = buffer.toByteBuffer
		def toArrayBuffer(buffer: PolymorphicBuffer): ArrayBuffer = buffer.toArrayBuffer
	}
}
