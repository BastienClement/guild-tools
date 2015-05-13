package gtp3

import utils.Timeout

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Future
import scala.util.Success
import akka.actor.{ActorPath, ActorRef}
import gt.Global
import scodec.Attempt.Successful
import scodec.DecodeResult
import scala.concurrent.duration._
import scodec.bits.BitVector
import actors.Actors._

class DuplicatedFrame extends Exception

class Socket(val id: Long, var actor: SocketActor) {
	// Socket state
	private var attached = true
	def isAttached = attached

	// Incoming sequence id
	private var in_seq = 0

	// Outgoing sequence controls
	private var out_seq = 0
	private var out_ack = 0
	private val out_buffer = mutable.Queue[SequencedFrame]()

	// Channels
	private val channels = mutable.Map[Int, Channel]()
	private val channels_pending = mutable.Map[Int, Future[Channel]]()
	private val channelid_pool = new NumberPool()

	// Limit the number of emitter REQUEST_ACK commands
	private var request_ack_cooldown = 0

	/**
	 * Overloaded output helper
	 */
	object out {
		def !(frame: Frame): Unit = {
			// Special handling for sequenced frames
			// Automatic tagging
			frame.ifSequenced { seq_frame =>
				// Get buffer length
				val buf_len = out_buffer.length

				// Check limits
				if (buf_len >= 128) {
					throw new Exception("Output buffer is full")
				} else if (buf_len > 16) {
					if (request_ack_cooldown <= 0) {
						this ! RequestAckFrame()
						request_ack_cooldown = 3
					} else {
						request_ack_cooldown -= 1
					}
				}

				// The next sequence id
				out_seq = (out_seq + 1) & 0xFFFF
				seq_frame.seq = out_seq

				// Save the frame in the output queue
				out_buffer.enqueue(seq_frame)
			}

			// Send the frame if the socket is open
			if (attached) actor.out ! Frame.encode(frame).toByteArray
		}
	}

	// Send the Handshake frame to the client
	out ! HandshakeFrame(GTP3Magic, Global.serverVersion, id)
	println("socket open", id)

	/**
	 * Receive a frame
	 */
	def receive(buffer: Array[Byte]) = try {
		// Decode the frame buffer
		val frame = Frame.decode(buffer)

		println(frame)

		// Handle sequenced frame acks
		frame.ifSequenced(handleSequenced)

		// Dispatch
		frame match {
			case AckFrame(last_seq) => ack(last_seq)

			case PingFrame() => out ! PongFrame()
			case PongFrame() =>
			case RequestAckFrame() => out ! AckFrame(in_seq)

			case f: ByeFrame => receiveBye(f)
			case f: IgnoreFrame => /* ignore */

			case f: OpenFrame => receiveOpen(f)
			case f: OpenSuccessFrame => receiveOpenSuccess(f)
			case f: OpenFailureFrame => receiveOpenFailure(f)
			case f: ResetFrame => receiveReset(f)

			case f: ChannelFrame => receiveChannelFrame(f)

			case _ => println("Unknown frame", frame)
		}
	} catch {
		case e: DuplicatedFrame => /* ignore duplicated frames */
	}

	/**
	 * Handle sequenced frames acknowledgments
	 */
	private def handleSequenced(frame: SequencedFrame) = {
		// The seuqence number for this frame
		val seq = frame.seq

		// Ensure the frame was not already received
		if (seq <= in_seq && (seq != 0 || in_seq == 0)) {
			throw new DuplicatedFrame
		}

		// Store the sequence number as the last received one
		in_seq = seq

		// Only send an actual ACK if multiple of 4
		if (seq % 4 == 0) {
			out ! AckFrame(seq)
		}
	}

	/**
	 * Handle received acknowledgments
	 */
	private def ack(seq: Int) = {
		// Dequeue while the frame sequence id is less or equal to the acknowledged one
		// Also dequeue if the frame is simply greater than the last acknowledgment, this handle
		// the wrap-around case
		@tailrec def purge(): Unit = {
			if (out_buffer.isEmpty) return
			val f = out_buffer.head
			if (f.seq <= seq || f.seq > this.out_ack) {
				this.out_buffer.dequeue()
				purge()
			}
		}

		// Purge the output buffer
		purge()

		// Save the sequence number as the last one received
		out_ack = seq
	}

	private def receiveBye(frame: ByeFrame) = {

	}

	private def receiveOpen(frame: OpenFrame) = {
		val request = new ChannelRequest(frame.channel_type, frame.token) {
			def accept(): Channel = {
				if (replied) throw new Exception()

				val id = channelid_pool.next
				val channel = new Channel(Socket.this, id, frame.sender_channel)

				_replied = true
				channels += (id -> channel)
				out ! OpenSuccessFrame(0, frame.sender_channel, id)

				channel
			}

			def reject(code: Int, message: String): Unit = {
				if (replied) return
				_replied = true
				out ! OpenFailureFrame(0, frame.sender_channel, code, message)
			}
		}

		println("received open", request)
	}

	private def receiveOpenSuccess(frame: OpenSuccessFrame) = {}
	private def receiveOpenFailure(frame: OpenFailureFrame) = {}
	private def receiveReset(frame: ResetFrame) = {}

	private def receiveChannelFrame(frame: ChannelFrame) = {
		channels.get(frame.channel) match {
			case Some(channel) => channel.receive(frame)
			case None => out ! ResetFrame(frame.channel)
		}
	}

	private[gtp3] def channelClosed(channel: Channel) = {
		val id = channel.id
		channels.remove(id)
		channelid_pool.release(id)
	}

	/**
	 * Rebind a detached socket
	 */
	def rebind(a: SocketActor, last_seq: Int) = {
		attached = true
		actor = a
		out ! SyncFrame(in_seq)
	}

	/**
	 * Detach a socket if the backing WebSocket is closed
    */
	def detach() = {
		attached = false
		actor = null
	}

	def closed() = {
		println("socket closed", id)
	}
}
