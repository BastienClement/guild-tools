package gtp3

import actors.AuthService
import akka.actor._
import gt.GuildTools
import gtp3.Socket._
import model.User
import scala.annotation.tailrec
import scala.collection.mutable
import scala.compat.Platform

object Socket {
	// Construct Socket actors
	def props(id: Long, opener: Opener) = Props(new Socket(id, opener))

	// Open request responses
	private case class ChannelAccept(open: OpenFrame, handler: Props)
	private case class ChannelReject(open: OpenFrame, code: Int, message: String)

	// Special exception used if a frame is received multiple times
	private case object DuplicatedFrame extends Exception

	// Handshake messages
	case class Handshake(out: ActorRef)
	case class Resume(out: ActorRef, seq: Int)
	case object Disconnect
	case object ForceStop

	// Incoming and outgoing frames
	case class IncomingFrame(buf: Array[Byte])
	case class OutgoingFrame(buf: Array[Byte])

	// Define the authenticated user of this socket
	case class SetUser(user: User, session: String)

	// The opener of a socket
	case class Opener(ip: String, ua: Option[String])

	// Request socket informations
	case object RequestInfos
	case class SocketInfos(user: User, state: String, open: Long, opener: Opener, channels: Seq[(Int, String)])
}

class Socket(val id: Long, val opener: Opener) extends Actor {
	// The outgoing frame receiver
	private var out: ActorRef = null

	// Incoming sequence id
	private var in_seq = 0

	// Outgoing sequence controls
	private var out_seq = 0
	private var out_ack = 0
	private val out_buffer = mutable.Queue[SequencedFrame]()

	// Channels
	private val channels = mutable.Map[Int, ActorRef]()
	private val channels_type = mutable.Map[Int, String]()
	private val channelid_pool = new NumberPool()

	// Limit the number of emitter REQUEST_ACK commands
	private var request_ack_cooldown = 0

	// Socket is authenticated
	private val open_time = Platform.currentTime
	private var user: User = null
	private var state = "NEW"

	def receive = {
		// Initialize a new socket
		case Handshake(o) =>
			out = o
			state = "OPEN"
			self ! HandshakeFrame(GTP3Magic, GuildTools.serverVersion.value, id)

		// Resume a disconnected socket
		case Resume(o, seq) =>
			out = o
			state = "OPEN"
			ack(seq)
			while (out_buffer.nonEmpty) self ! out_buffer.dequeue()
			self ! SyncFrame(in_seq)

		case Disconnect =>
			state = "DISCONNECTED"
			out = null

		case ForceStop | AuthService.SessionClosed =>
			state = "KILLED"
			if (out != null) {
				out ! Kill
			}
			self ! Kill

		// Received a buffer from the WebSocket
		case IncomingFrame(buffer) =>
			try {
				// Decode the frame buffer
				val frame = Frame.decode(buffer)

				// Handle sequenced frame acks
				frame.ifSequenced(handleSequenced)

				// Dispatch
				frame match {
					case AckFrame(last_seq) => ack(last_seq)

					case PingFrame() => self ! PongFrame()
					case PongFrame() =>
					case RequestAckFrame() => self ! AckFrame(in_seq)

					case f: IgnoreFrame => /* ignore */

					case f: OpenFrame => receiveOpen(f)
					case f: OpenSuccessFrame => receiveOpenSuccess(f)
					case f: OpenFailureFrame => receiveOpenFailure(f)
					case f: ResetFrame => receiveReset(f)

					case f: ChannelFrame => receiveChannelFrame(f)

					case _ => println("Unknown frame", frame)
				}
			} catch {
				case DuplicatedFrame => /* ignore duplicated frames */
			}

		// A frame object to be encoded and sent over WebSocket
		case frame: Frame =>
			// Special handling for sequenced frames
			// Automatic tagging
			frame.ifSequenced { seq_frame =>
				// Get buffer length
				val buf_len = out_buffer.length

				// Check limits
				if (buf_len >= 256) {
					throw new Exception("Output buffer is full")
				} else if (buf_len > 32) {
					if (request_ack_cooldown <= 0) {
						self ! RequestAckFrame()
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

			// Send the frame
			if (out != null) out ! OutgoingFrame(Frame.encode(frame).toByteArray)

		// A channel open request is accepted
		case ChannelAccept(open, handler_props) =>
			val id = channelid_pool.next
			val channel = context.actorOf(Channel.props(self, id, open.sender_channel, handler_props))
			context.watch(channel)
			channels += id -> channel
			channels_type += id -> open.channel_type
			self ! OpenSuccessFrame(0, open.sender_channel, id)

		// A channel open request is rejected
		case ChannelReject(open, code, message) =>
			self ! OpenFailureFrame(0, open.sender_channel, code, message)

		// Update the user attached to the socket for future open requests
		case SetUser(u, session) =>
			user = u
			AuthService.subscribe(session)

		// A channel actor is terminated, remove the channel
		case Terminated(channel) =>
			for ((id, chan) <- channels if chan == channel) {
				channels.remove(id)
				channels_type.remove(id)
				channelid_pool.release(id)
			}

		// Request socket informations
		case RequestInfos =>
			sender ! SocketInfos(user, state, open_time, opener, channels_type.toSeq)
	}

	/**
	 * Handle sequenced frames acknowledgments
	 */
	private def handleSequenced(frame: SequencedFrame) = {
		// The seuqence number for this frame
		val seq = frame.seq

		// Ensure the frame was not already received
		if (seq <= in_seq && (seq != 0 || in_seq == 0)) throw DuplicatedFrame

		// Store the sequence number as the last received one
		in_seq = seq

		// Only send an actual ACK if multiple of 4
		if (seq % 4 == 0) self ! AckFrame(seq)
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
			if (f.seq <= seq || (f.seq > out_ack && seq < out_ack)) {
				out_buffer.dequeue()
				purge()
			}
		}

		// Purge the output buffer
		purge()

		// Save the sequence number as the last one received
		out_ack = seq
	}

	private def receiveOpen(frame: OpenFrame) = {
		val request = new ChannelRequest(self, frame.channel_type, frame.token, user, opener) {
			def accept(handler: Props): Unit = {
				if (replied) throw new Exception("Request already responded to")
				_replied = true
				self ! ChannelAccept(frame, handler)
			}

			def reject(code: Int, message: String): Unit = {
				if (replied) throw new Exception("Request already responded to")
				_replied = true
				self ! ChannelReject(frame, code, message)
			}
		}

		if (user == null && frame.channel_type != "auth") {
			request.reject(103, "Non-authenticated socket cannot request channel")
		} else {
			ChannelValidators.get(frame.channel_type) match {
				case Some(validator) => validator.open(request)
				case None => request.reject(104, "Unknown channel type")
			}
		}
	}

	private def receiveOpenSuccess(frame: OpenSuccessFrame) = {}
	private def receiveOpenFailure(frame: OpenFailureFrame) = {}
	private def receiveReset(frame: ResetFrame) = {}

	private def receiveChannelFrame(frame: ChannelFrame) = {
		channels.get(frame.channel) match {
			case Some(channel) => channel ! frame
			case None => self ! ResetFrame(frame.channel)
		}
	}
}
