package gtp3

import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.Success
import akka.actor.{ActorPath, ActorRef}
import gt.Global
import scodec.Attempt.Successful
import scodec.DecodeResult
import scodec.bits.BitVector

class DuplicatedFrame extends Exception

class Socket(val id: Long, var actor: SocketActor) {
	// Send the Handshake frame to the client
	out ! HandshakeFrame(GTP3Magic, Global.serverVersion, id)

	// Socket state
	private var attached = true

	// Incoming sequence id
	private var in_seq = 0

	// Outgoing sequence controls
	private var out_seq = 0
	private var out_ack = 0
	private val out_buffer = mutable.Queue[SequencedFrame]()

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
						this ! CommandFrame(CommandCode.REQUEST_ACK)
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

	/**
	 * Receive a frame
	 */
	def receive(buffer: Array[Byte]) = try {
		// Decode the frame buffer
		val frame = Frame.decode(buffer)

		// Handle sequenced frame acks
		frame.ifSequenced(handleSequenced)

		// Dispatch
		frame match {
			case AckFrame(last_seq) => ack(last_seq)
			case CommandFrame(c) => command(c)
			case _ => println("Unknown frame", frame)
		}
	} catch {
		case e: DuplicatedFrame => /* ignore duplicated frames */
	}

	/**
	 * Handle sequenced frames acknowledgments
	 */
	def handleSequenced(frame: SequencedFrame) = {
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
	def ack(seq: Int) = {
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

	/**
	 * Execute commands
    */
	def command(code: Int) = code match {
		case CommandCode.PING => out ! CommandFrame(CommandCode.PONG)
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
}
