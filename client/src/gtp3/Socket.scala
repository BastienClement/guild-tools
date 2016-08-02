package gtp3

import org.scalajs.dom.raw.Event
import org.scalajs.dom.{MessageEvent, WebSocket, console, window}
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.DynamicImplicits._
import scala.scalajs.js.timers._
import scala.scalajs.js.typedarray.ArrayBuffer
import util.EventSource
import util.buffer.BufferOps
import util.implicits._
import xuen.rx.Var

class Socket(private val url: String) {
	// The two parts of the 64 bits socket-id
	private[this] var id: Long = 0L

	// The underlying websocket and connection state
	private[this] var ws: WebSocket = _
	private[this] var state: SocketState = SocketState.Uninitialized

	// Available channels
	private[this] val channels = mutable.Map.empty[Int, Channel]
	private[this] val channelsPending = mutable.Map.empty[Int, Promise[Int]]
	private[this] val channelIdPool = new NumberPool(Protocol.ChannelsLimit)

	// Last received frame sequence number
	private[this] var inSeq = 0

	// Output queue and sequence numbers
	private[this] var outSeq = 0
	private[this] var outAck = 0
	private[this] val outBuffer = mutable.Queue.empty[SequencedFrame]

	// Reconnect attempts counter
	private[this] var retryCount = 0

	// Limit the number of REQUEST_ACK commands
	private[this] var requestAckCooldown = 0
	private[this] var paused = false

	// Last ping time
	private[this] var pingTime = 0.0
	val latency: Var[Double] = 0.0

	// Tracing mode
	var verbose = false

	// Events
	val onConnect = new EventSource[String]
	val onReconnect = new EventSource.Simple
	val onClose = new EventSource[String]
	val onDisconnect = new EventSource.Simple
	val onReset = new EventSource.Simple
	val onRequestStart = new EventSource.Simple
	val onRequestEnd = new EventSource.Simple

	/** Connects to the server */
	def connect(): Unit = {
		if (ws != null || state == SocketState.Closed) {
			throw GTP3Error("Cannot connect() on an already open or closed socket")
		}

		// Create the websocket
		ws = new WebSocket(url, "GTP3-WS")
		ws.binaryType = "arraybuffer"

		// Reconnect on error or socket closed
		var closed_once = false
		ws.onerror = (e: Event) => if (!closed_once) {
			closed_once = true
			if (e.dyn.wasClean) {
				onClose.emit(e.dyn.reason.asInstanceOf[String])
				closed_once = true
				reconnect()
			}
		}

		// Handle message processing
		ws.onmessage = (ev: MessageEvent) => {
			try {
				receive(ev.data.asInstanceOf[ArrayBuffer].toByteArray)
			} catch {
				case DuplicatedFrame =>
				// The DuplicatedFrame is thrown by the ack() method and is used to
				// interrupt message processing if the sequence number indicate that
				// the frame was already handled once. We just ignore it here.
				case ex: Throwable => throw ex
			}
		}

		// Client initiate the handshake
		ws.onopen = (_: Event) => handshake()
	}

	/** Generate the correct handshake frame given the current state */
	private def handshake(): Unit = {
		val frame = state match {
			case SocketState.Uninitialized => HelloFrame(Protocol.GTP3, s"GuildTools Client 7.0 [${ window.navigator.userAgent }]")
			case SocketState.Reconnecting => ResumeFrame(id, inSeq)
			case _ => throw GTP3Error(s"Cannot generate handshake from state '$state'")
		}

		state = SocketState.Open
		retryCount = 0

		rawSend(frame)
	}

	/** Handles reconnection logic */
	private def reconnect(): Unit = {
		// Check if the socket is already closed
		if (state == SocketState.Closed) return

		// Transition from Ready to Reconnect
		if (state == SocketState.Ready) {
			state = SocketState.Reconnecting
			onReconnect.emit()
		}

		// Check retry count and current state
		if (state != SocketState.Reconnecting || retryCount > Protocol.ReconnectAttempts) {
			close()
			return
		}

		// Expentional backoff timer between reconnects
		ws = null
		retryCount += 1

		setTimeout((2 ** retryCount) * 500) {
			connect()
		}
	}

	/** Close the socket */
	def close(): Unit = {
		// Ensure the socket is not closed more than once
		if (state == SocketState.Closed) return
		state = SocketState.Closed

		// Emit event
		onDisconnect.emit()

		// Actually close the WebSocket
		ws.close()
		ws = null

		// Close open channels
		channels.values.foreach { chan => chan.close(-1, "Socket closed") }
	}

	/** Puts the socket back in ready state */
	private def ready(version: String = null): Unit = {
		state = SocketState.Ready
		onConnect.emit(version)
	}

	/** Sends a PING message and compute RTT latency */
	def ping(): Unit = {
		pingTime = System.nanoTime() / 1000000
		send(PingFrame())
	}

	/** Opens a new channel on this socket */
	def openChannel(tpe: String, token: String = ""): Future[Channel] = openChannel(tpe, token, 0)

	/** Opens a new channel on this socket */
	private[gtp3] def openChannel(tpe: String, token: String, parent: Int): Future[Channel] = {
		val id = channelIdPool.next
		val promise = Promise[Int]()
		channelsPending.put(id, promise)

		// Timeout for server to send OPEN_SUCCESS or OPEN_FAILURE
		setTimeout(Protocol.OpenTimeout) {
			if (!promise.isCompleted) {
				promise.failure(GTP3Error("Timeout"))
			}
		}

		// Send the open message to the server
		send(OpenFrame(0, id, tpe, token, parent))

		promise.future.map { remote =>
			val channel = new Channel(this, tpe, id, remote)
			channelsPending.remove(id)
			channels.put(id, channel)
			channel
		} andThen {
			case _ => channelsPending.remove(id)
		}
	}

	/**
	  * Send acknowledgment to remote
	  * This function also ensure that we do not receive a frame multiple times
	  * and reduce network usage for acknowledgments by only sending one
	  * every AckInterval messages.
	  */
	private def sendAck(seq: Int): Unit = {
		// Ensure the frame was not already received
		if (seq <= inSeq && (seq != 0 || inSeq == 0)) {
			throw DuplicatedFrame
		}

		// Store the sequence number as the last received one
		inSeq = seq

		// Only send an actual ACK if multiple of AckInterval
		if (seq % Protocol.AckInterval == 0) {
			send(AckFrame(seq))
		}
	}

	/** Receive a new frame, dispatch */
	private def receive(data: Array[Byte]) = {
		val frame = Frame.decode(data)
		if (verbose) trace("<<", frame)
		frame.ifSequenced { seq => sendAck(seq.seq) }

		frame match {
			case cf: ChannelFrame =>
				receiveChannelFrame(cf)

			case HandshakeFrame(magic, version, sockid) =>
				receiveHandshackFrame(magic, version, sockid)

			case SyncFrame(lastSeq) =>
				receiveSyncFrame(lastSeq)

			case AckFrame(lastSeq) =>
				receiveAckFrame(lastSeq)

			case IgnoreFrame(_) => // ignore

			case PingFrame() =>
				send(PongFrame())

			case PongFrame() =>
				latency := (System.nanoTime() / 1000000) - pingTime

			case RequestAckFrame() =>
				send(AckFrame(inSeq))

			case OpenFrame(_, remoteid, channelType, token, parent) =>
				receiveOpenFrame(remoteid, channelType, token, parent)

			case OpenSuccessFrame(_, localid, remoteid) =>
				receiveOpenSuccessFrame(localid, remoteid)

			case OpenFailureFrame(_, localid, code, message) =>
				receiveOpenFailureFrame(localid, code, message)

			case ResetFrame(remoteid) =>
				receiveResetFrame(remoteid)

			case _ =>
				protocolError()
		}
	}

	/** Generic channel frame handler */
	private def receiveChannelFrame(frame: ChannelFrame): Unit = {
		channels.get(frame.channel) match {
			case None => send(ResetFrame(frame.channel))
			case Some(channel) => channel.receive(frame)
		}
	}

	/** Handshake message, socket is ready */
	private def receiveHandshackFrame(magic: Int, version: String, sockid: Long): Unit = {
		if (state != SocketState.Open || magic != Protocol.GTP3) {
			protocolError()
		}

		if (id != 0) reset()
		id = sockid

		ready(version)
	}

	/** Resync the socket with the server */
	private def receiveSyncFrame(lastSeq: Int): Unit = {
		if (state != SocketState.Open) {
			protocolError()
		}

		// Treat the Sync message as an acknowledgment
		// This will remove any queued frames not acknowledged but in fact received
		// SyncFrame is compatible with AckFrame
		receiveAckFrame(lastSeq)

		ready()

		// Now, what's left in the buffer is only unreceived frame
		outBuffer.foreach(frame => send(frame))
	}

	/** Received a ACK from the server, clean outgoing queue */
	private def receiveAckFrame(lastSeq: Int): Unit = {
		// Dequeue while the frame sequence id is less or equal to the acknowledged one
		// Also dequeue if the frame is simply greater than the last acknowledgment, this handle
		// the wrap-around case
		var break = false
		while (outBuffer.nonEmpty && !break) {
			val frame = outBuffer.head
			if (frame.seq <= lastSeq || (frame.seq > outAck && lastSeq < outAck)) {
				outBuffer.dequeue()
			} else {
				break = true
			}
		}

		// Check if we can emit a resume event
		val outBufferLength = outBuffer.size

		if (paused && outBufferLength < Protocol.BufferPauseLimit) {
			channels.values.foreach { chan => chan.resume(outBufferLength) }
			this.paused = false
		}

		// Save the sequence number as the last one received
		outAck = lastSeq
	}

	/** Channel open request */
	private def receiveOpenFrame(remoteid: Int, channelType: String, token: String, parent: Int): Unit = {
		// Server can't open a channel for now
		throw new UnsupportedOperationException()
	}

	/** Channel successfully open */
	private def receiveOpenSuccessFrame(localid: Int, remoteid: Int): Unit = {
		channelsPending.get(localid) match {
			case None => send(ResetFrame(remoteid))
			case Some(promise) => promise.success(remoteid)
		}
	}

	/** Failure to open a channel */
	private def receiveOpenFailureFrame(localid: Int, code: Int, message: String): Unit = {
		channelsPending.get(localid) match {
			case None => // nothing
			case Some(promise) =>
				promise.failure(GTP3Error(s"Failure to open channel: [$code] $message"))
		}
	}

	/** Destroy a server-side undefined channel */
	private def receiveResetFrame(remoteid: Int): Unit = {
		// Close any channel matching the DESTROY message
		for (channel <- channels.values if channel.remote == remoteid) {
			channel.reset()
		}
	}

	/** Reconnected to the server, but unable to restore context */
	private def reset(): Unit = {
		// Send reset on channels
		channels.values.forall(c => c.reset())

		// Clear own state
		channels.clear()
		channelsPending.clear()
		channelIdPool.clear()
		inSeq = 0
		outSeq = 0
		outAck = 0
		outBuffer.clear()

		// Emit reset event
		onReset.emit()
	}

	/** Send a complete frame */
	private[gtp3] def send(frame: Frame): Unit = {
		// Add the sequence number to the frame
		frame match {
			case sequenced: SequencedFrame if sequenced.seq == 0 =>
				// Check the size of the output buffer
				val outBufferLen = outBuffer.size
				if (outBufferLen >= Protocol.BufferHardLimit) {
					throw GTP3Error("Output buffer is full")
				} else if (outBufferLen >= Protocol.BufferSoftLimit) {
					requestAckCooldown -= 1
					if (requestAckCooldown <= 0) {
						send(RequestAckFrame())
						requestAckCooldown = Protocol.RequestAckCooldown
						if (outBufferLen >= Protocol.BufferPauseLimit) {
							channels.values.foreach { chan => chan.pause(outBufferLen) }
							paused = true
						}
					}
				}

				// Compute the next sequence number
				outSeq = (outSeq + 1) & 0xFFFF

				// Tag the frame
				sequenced.seq = outSeq

				// Push the frame in the output buffer for later replay
				outBuffer.enqueue(sequenced)

			case _ => // ignore
		}

		if (state == SocketState.Ready) {
			rawSend(frame)
		}
	}

	private def rawSend(frame: Frame): Unit = {
		if (verbose) trace(">>", frame)
		val buffer = Frame.encode(frame).toArrayBuffer

		// Ensure a maximum frame size
		if (buffer.byteLength > Protocol.FrameLimit) {
			throw GTP3Error("Frame size limit exceeded")
		}

		ws.send(buffer)
	}

	/** Print the socket activity */
	private def trace(direction: String, frame: Frame): Unit = {
		val frameName = frame.getClass.getName.split("\\.").last
		val padding = " " * (17 - frameName.length)
		val str = frame.toString
		val formatted = str.substring(str.indexOf("("))
		                .replaceAll("ByteVector\\((.*?), 0x[0-9a-f]+\\)", "ByteVector($1)")

		// Output frame data
		console.dyn.debug(direction, frameName + padding, formatted)
	}

	/** Handle force closing the socket due to protocol error */
	private def protocolError(): Nothing = {
		close()
		throw GTP3Error("Protocol error")
	}

	/** Called by channels when closed */
	private[gtp3] def channelClosed(channel: Channel): Unit = {
		channels.remove(channel.id)
		channelIdPool.release(channel.id)
	}
}
