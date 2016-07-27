package gtp3

import boopickle.Pickler
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scodec.bits.ByteVector
import util.EventSource

/** Channel implementation */
class Channel private[gtp3] (val socket: Socket, val tpe: String, val id: Int, val remote: Int) {
	// Current channel state
	private[this] var state: ChannelState = ChannelState.Open

	// The id of the next outgoing request
	private[this] val requestIdPool = new NumberPool(Protocol.InflightRequests)

	// Pending requests
	private[this] val requests = mutable.Map.empty[Int, Promise[_]]

	// Event
	val onMessage = new EventSource[(String, PickledPayload)]
	val onClose = new EventSource[(Int, String)]

	/** Send a message without expecting a reply */
	def send[P: Pickler](message: String, data: P): Unit = {
		// Encode payload
		for ((buffer, flags) <- Payload.encode(data)) {
			val frame = MessageFrame(0, remote, message, flags, buffer.toByteVector)
			socket.send(frame)
		}
	}

	/** Send a request and expect a reply */
	def request[P: Pickler](request: String, data: P, silent: Boolean = false): Future[PickledPayload] = {
		// Allocate request ID
		val id = requestIdPool.next

		// Create promise
		val promise = Promise[PickledPayload]()
		requests.put(id, promise)

		// Loading indicator
		if (!silent) {
			socket.onRequestStart.emit()
			promise.future.andThen {
				case _ => socket.onRequestEnd.emit()
			}
		}

		// Encode payload
		for ((buffer, flags) <- Payload.encode(data)) {
			val frame = RequestFrame(0, remote, request, id, flags, buffer.toByteVector)
			socket.send(frame)
		}

		promise.future
	}

	/** Open a new sub-channel */
	def openChannel(channelType: String, token: String): Future[Channel] = {
		socket.openChannel(channelType, token, id)
	}

	/** Close the channel and attempt to flush output buffer */
	def close(code: Int, reason: String): Unit = {
		// Ensure we don't close the channel multiple times
		if (state == ChannelState.Closed) return
		state = ChannelState.Closed

		// Send close message to remote and local listeners
		socket.send(CloseFrame(0, remote, code, reason))
		onClose.emit((code, reason))

		socket.channelClosed(this)
	}

	/** Receive a channel specific frame */
	private[gtp3] def receive(frame: ChannelFrame): Unit = frame match {
		case MessageFrame(_, _, message, flags, payload) =>
			receiveMessageFrame(message, flags, payload)

		case RequestFrame(_, _, request, rid, flags, payload) =>
			receiveRequestFrame(request, rid, flags, payload)

		case SuccessFrame(_, _, request, flags, payload) =>
			receiveSuccessFrame(request, flags, payload)

		case FailureFrame(_, _, request, code, message, stack) =>
			receiveFailureFrame(request, code, message, stack)

		case CloseFrame(_, _, code, message) =>
			close(code, message)
	}

	/** Received a message frame */
	private def receiveMessageFrame(message: String, flags: Int, payload: ByteVector): Unit = {
		for (buffer <- Payload.inflate(payload, flags)) {
			onMessage.emit((message, new PickledPayload(buffer.toByteBuffer)))
		}
	}

	/** Received a request frame */
	private def receiveRequestFrame(request: String, id: Int, flags: Int, payload: ByteVector): Unit = {
		throw GTP3Error("Server-initiated request are not yet supported")
	}

	/** Fetches the promise assocated with the given request ID */
	private def getRequestPromise(rid: Int): Option[Promise[_]] = {
		val promise = requests.get(rid)
		if (promise.isDefined) {
			requests.remove(rid)
			requestIdPool.release(rid)
		}
		promise
	}

	/** Received a Success frame */
	private def receiveSuccessFrame(request: Int, flags: Int, payload: ByteVector): Unit = {
		for (promise <- getRequestPromise(request)) {
			promise.asInstanceOf[Promise[Any]].success(new PickledPayload(payload.toByteBuffer))
		}
	}

	/** Received a Failure frame */
	private def receiveFailureFrame(request: Int, code: Int, message: String, stack: String): Unit = {
		for (promise <- getRequestPromise(request)) {
			promise.failure(RequestError(message, code, stack))
		}
	}

	private[gtp3] def reset() = throw GTP3Error("Channel reset is not yet supported")
	private[gtp3] def pause(outBufferLen: Int): Unit = throw GTP3Error("Channel pause is not yet supported")
	private[gtp3] def resume(outBufferLength: Int): Unit = throw GTP3Error("Channel resume is not yet supported")
}
