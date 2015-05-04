package actors

import java.io.{ByteArrayOutputStream, DataOutputStream, DataInputStream, ByteArrayInputStream}

import akka.actor.{PoisonPill, Actor, ActorRef}
import play.api.mvc.RequestHeader

import scala.annotation.switch
import scala.util.Random

import GTP3._

private object GTP3 {
	final val PROTOCOL_MAGIC = 0x47545033

	// Connection control
	final val HELLO = 0x10
	final val RESUME = 0x11
	final val ACK = 0x12
	final val BYE = 0x13

	// Connection messages
	final val IGNORE = 0x20
	final val PING = 0x21
	final val PONG = 0x22
	final val ACK_REQ = 0x23

	// Channel control
	final val OPEN = 0x30
	final val OPEN_SUCCESS = 0x31
	final val OPEN_FAILURE = 0x32
	final val CLOSE = 0x33
	final val RESET = 0x34

	// Channel messages
	final val MESSAGE = 0x40
	final val REQUEST = 0x41
	final val SUCCESS = 0x42
	final val FAILURE = 0x43

	private val rand = new Random()
	def nextSocketID = rand.nextLong()
}

object GTP3Frame {
	def apply(buffer: Array[Byte]) = new GTP3ReadableFrame(buffer)
	def apply(opcode: Int) = new GTP3WritableFrame(opcode.toByte)
}

trait GTP3Frame {
	val opcode: Byte
}

class GTP3ReadableFrame(buffer: Array[Byte]) extends GTP3Frame {
	val data = new DataInputStream(new ByteArrayInputStream(buffer))
	val opcode = data.readByte()

	def nextInt = data.readInt()
	def nextLong = data.readLong()
}

class GTP3WritableFrame(val opcode: Byte) extends GTP3Frame {
	val stream = new ByteArrayOutputStream()
	val data = new DataOutputStream(stream)
	data.writeByte(opcode)

	def write(v: Int) = data.writeInt(v)
	def write(v: Long) = data.writeLong(v)

	def buffer = {
		data.flush()
		stream.toByteArray
	}
}

object ProtocolError extends Throwable

class GTP3Socket(val output: ActorRef, val request: RequestHeader) extends Actor {
	def receive = {
		case input: Array[Byte] =>
			try {
				parse(input)
			} catch {
				case ProtocolError =>
					self ! PoisonPill
					println("Protocol error")
			}
	}

	def parse(buffer: Array[Byte]) = {
		val frame = GTP3Frame(buffer)

		(frame.opcode: @switch) match {
			case HELLO => hello(frame)
			case RESUME => resume(frame)
			case ACK => ack(frame)
			case BYE => bye(frame)
			case IGNORE => /* ignore */
			case PING => ping(frame)
			case PONG => pong(frame)
			case ACK_REQ => ackReq(frame)
			case OPEN => open(frame)
			case OPEN_SUCCESS => openSuccess(frame)
			case OPEN_FAILURE => openFailure(frame)
			case CLOSE => close(frame)
			case RESET => reset(frame)
			case MESSAGE => message(frame)
			case REQUEST => request(frame)
			case SUCCESS => success(frame)
			case FAILURE => failure(frame)
			case _ => throw ProtocolError
		}
	}

	def hello(frame: GTP3ReadableFrame) = {
		val magic_number = frame.nextInt
		if (magic_number != PROTOCOL_MAGIC)
			throw ProtocolError

		val id = GTP3.nextSocketID
		println(id)

		val out = GTP3Frame(HELLO)
		out.write(PROTOCOL_MAGIC)
		out.write(id)

		output ! out.buffer
	}

	def resume(frame: GTP3ReadableFrame) = {
		val id = frame.nextLong
		println(id)

		val out = GTP3Frame(RESUME)
		out.write(0x00)

		output ! out.buffer
	}

	def ack(frame: GTP3ReadableFrame) = {}
	def bye(frame: GTP3ReadableFrame) = {}
	def ping(frame: GTP3ReadableFrame) = {}
	def pong(frame: GTP3ReadableFrame) = {}
	def ackReq(frame: GTP3ReadableFrame) = {}
	def open(frame: GTP3ReadableFrame) = {}
	def openSuccess(frame: GTP3ReadableFrame) = {}
	def openFailure(frame: GTP3ReadableFrame) = {}
	def close(frame: GTP3ReadableFrame) = {}
	def reset(frame: GTP3ReadableFrame) = {}
	def message(frame: GTP3ReadableFrame) = {}
	def request(frame: GTP3ReadableFrame) = {}
	def success(frame: GTP3ReadableFrame) = {}
	def failure(frame: GTP3ReadableFrame) = {}

}
