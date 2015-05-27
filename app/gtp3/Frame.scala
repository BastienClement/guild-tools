package gtp3

import scodec._
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs._

private object g {
	val str = variableSizeBytes(uint16, utf8)
	val bool = scodec.codecs.bool(8)
	val buf = variableSizeBytes(uint16, bytes)
}

sealed trait Frame {
	def ifSequenced(thunk: (SequencedFrame) => Unit) = {}
}

sealed trait SequencedFrame extends Frame{
	var seq: Int
	override def ifSequenced(thunk: (SequencedFrame) => Unit) = thunk(this)
}

sealed trait ChannelFrame extends SequencedFrame {
	val channel: Int
}

sealed trait PayloadFrame {
	val flags: Int
	val payload: ByteVector
}

case class BadFrame() extends Frame
object BadFrame {
	implicit val discriminator = Discriminator[Frame, BadFrame, Int](0xFF)
	implicit val codec = fail(Err.General("", Nil)).as[BadFrame]
}

case class HelloFrame(magic: Int, version: String) extends Frame
object HelloFrame {
	implicit val discriminator = Discriminator[Frame, HelloFrame, Int](FrameType.HELLO)
	implicit val codec = (int32 :: g.str).as[HelloFrame]
}

case class HandshakeFrame(magic: Int, version: String, sockid: Long) extends Frame
object HandshakeFrame {
	implicit val discriminator = Discriminator[Frame, HandshakeFrame, Int](FrameType.HANDSHAKE)
	implicit val codec = (int32 :: g.str :: int64).as[HandshakeFrame]
}

case class ResumeFrame(sockid: Long, last_seq: Int) extends Frame
object ResumeFrame {
	implicit val discriminator = Discriminator[Frame, ResumeFrame, Int](FrameType.RESUME)
	implicit val codec = (int64 :: uint16).as[ResumeFrame]
}

case class SyncFrame(last_seq: Int) extends Frame
object SyncFrame {
	implicit val discriminator = Discriminator[Frame, SyncFrame, Int](FrameType.SYNC)
	implicit val codec = uint16.as[SyncFrame]
}

case class AckFrame(last_seq: Int) extends Frame
object AckFrame {
	implicit val discriminator = Discriminator[Frame, AckFrame, Int](FrameType.ACK)
	implicit val codec = uint16.as[AckFrame]
}

case class IgnoreFrame(padding: ByteVector) extends Frame
object IgnoreFrame {
	implicit val discriminator = Discriminator[Frame, IgnoreFrame, Int](FrameType.IGNORE)
	implicit val codec = bytes.as[IgnoreFrame]
}

case class PingFrame() extends Frame
object PingFrame {
	implicit val discriminator = Discriminator[Frame, PingFrame, Int](FrameType.PING)
}

case class PongFrame() extends Frame
object PongFrame {
	implicit val discriminator = Discriminator[Frame, PongFrame, Int](FrameType.PONG)
}

case class RequestAckFrame() extends Frame
object RequestAckFrame {
	implicit val discriminator = Discriminator[Frame, RequestAckFrame, Int](FrameType.REQUEST_ACK)
}

case class OpenFrame(var seq: Int, sender_channel: Int,
		channel_type: String, token: String, parent_channel: Int) extends Frame with SequencedFrame
object OpenFrame {
	implicit val discriminator = Discriminator[Frame, OpenFrame, Int](FrameType.OPEN)
	implicit val codec = (uint16 :: uint16 :: g.str :: g.str :: uint16).as[OpenFrame]
}

case class OpenSuccessFrame(var seq: Int, recipient_channel: Int, sender_channel: Int) extends Frame with SequencedFrame
object OpenSuccessFrame {
	implicit val discriminator = Discriminator[Frame, OpenSuccessFrame, Int](FrameType.OPEN_SUCCESS)
	implicit val codec = (uint16 :: uint16 :: uint16).as[OpenSuccessFrame]
}

case class OpenFailureFrame(var seq: Int, recipient_channel: Int, code: Int, message: String) extends Frame with SequencedFrame
object OpenFailureFrame {
	implicit val discriminator = Discriminator[Frame, OpenFailureFrame, Int](FrameType.OPEN_FAILURE)
	implicit val codec = (uint16 :: uint16 :: uint16 :: g.str).as[OpenFailureFrame]
}

case class ResetFrame(sender_channel: Int) extends Frame
object ResetFrame {
	implicit val discriminator = Discriminator[Frame, ResetFrame, Int](FrameType.RESET)
	implicit val codec = uint16.as[ResetFrame]
}

case class MessageFrame(var seq: Int, channel: Int,
		message: String, flags: Int, payload: ByteVector) extends Frame with ChannelFrame with PayloadFrame
object MessageFrame {
	implicit val discriminator = Discriminator[Frame, MessageFrame, Int](FrameType.MESSAGE)
	implicit val codec = (uint16 :: uint16 :: g.str :: uint16 :: g.buf).as[MessageFrame]
}

case class RequestFrame(var seq: Int, channel: Int,
		request: String, id: Int, flags: Int, payload: ByteVector) extends Frame with ChannelFrame with PayloadFrame
object RequestFrame {
	implicit val discriminator = Discriminator[Frame, RequestFrame, Int](FrameType.REQUEST)
	implicit val codec = (uint16 :: uint16 :: g.str :: uint16 :: uint16 :: g.buf).as[RequestFrame]
}

case class SuccessFrame(var seq: Int, channel: Int,
		request: Int, flags: Int, payload: ByteVector) extends Frame with ChannelFrame with PayloadFrame
object SuccessFrame {
	implicit val discriminator = Discriminator[Frame, SuccessFrame, Int](FrameType.SUCCESS)
	implicit val codec = (uint16 :: uint16 :: uint16 :: uint16 :: g.buf).as[SuccessFrame]
}

case class FailureFrame(var seq: Int, channel: Int,
		request: Int, code: Int, message: String) extends Frame with ChannelFrame
object FailureFrame {
	implicit val discriminator = Discriminator[Frame, FailureFrame, Int](FrameType.FAILURE)
	implicit val codec = (uint16 :: uint16 :: uint16 :: uint16 :: g.str).as[FailureFrame]
}

case class CloseFrame(var seq: Int, channel: Int, code: Int, message: String) extends Frame with ChannelFrame
object CloseFrame {
	implicit val discriminator = Discriminator[Frame, CloseFrame, Int](FrameType.CLOSE)
	implicit val codec = (uint16 :: uint16 :: uint16 :: g.str).as[CloseFrame]
}

object Frame {
	implicit val discriminated = Discriminated[Frame, Int](uint8)
	val codec = Codec.coproduct[Frame].auto.asInstanceOf[Codec[Frame]]
	def decode(buffer: Array[Byte]) = codec.decode(BitVector(buffer)).fold(_ => BadFrame(), res => res.value)
	def encode(frame: Frame) = codec.encode(frame).require
}
