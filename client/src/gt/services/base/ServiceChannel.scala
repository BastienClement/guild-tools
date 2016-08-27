package gt.services.base

import boopickle.DefaultBasic._
import gt.{App, Server}
import gtp3.{Channel, PickledPayload}
import org.scalajs.dom.console
import rx.{Rx, Var}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.language.implicitConversions
import scala.util.{Failure, Success}

/**
  * A service channel that handle automatic mangement of a GTP3 channel
  * required by a service.
  *
  * @param tpe      the type of channel to request
  * @param lzy      if true, the channel will not be opened until required
  * @param delegate the delegate used to handle incoming messages
  */
class ServiceChannel(tpe: String, lzy: Boolean, delegate: Delegate) {
	/** The channel instance */
	private[this] var channel: Channel = null

	/** Current channel status */
	private[this] val openStatus: Var[Boolean] = false

	/** A read-only view of the current status of this service channel */
	val open: Rx[Boolean] = openStatus

	/** Queue of pending messages of queries during channel opening */
	private[this] val queue = scalajs.js.Array[Channel => Unit]()

	/** Is true if the channel is currently being opened */
	private[this] var openPending = false

	/**
	  * Performs eager opening if the channel is not lazy.
	  * Automatically called when the owner channel is activated.
	  */
	private[services] def eagerOpen(): Unit = if (!lzy) lazyOpen()

	/**
	  * Lazily open the channel when it is first required.
	  */
	private[services] def lazyOpen(): Unit = {
		if (channel != null || openPending) return
		openPending = true

		Server.openChannel(tpe).andThen {
			case _ => openPending = false
		}.onComplete {
			case Success(chan) =>
				channel = chan
				openStatus := true

				chan.onMessage ~> (delegate.receiveMessage _).tupled
				chan.onClose ~> { case (code, reason) =>
					channel = null
					openStatus := false
				}

				while (queue.length > 0) {
					queue.shift().apply(chan)
				}

			case Failure(e) =>
				console.error(s"Failed to open service channel $tpe: ${ App.formatException(e) }")
		}
	}

	/**
	  * Closes the service channel
	  */
	private[services] def close(): Unit = if (channel != null) {
		channel.close()
		channel = null
		openStatus := false
	}

	/**
	  * Executes an action once the service channel is available.
	  *
	  * @param action the action to execute, taking the channel as argument
	  * @tparam T the type of the result of the action
	  * @return the future returned by the action
	  */
	private def withChannel[T](action: Channel => Future[T]): Future[T] = {
		if (channel != null) action(channel)
		else {
			val promise = Promise[T]()
			queue.push(chan => promise.completeWith(action(chan)))
			lazyOpen()
			promise.future
		}
	}

	/**
	  * Sends a message without arguments to the server.
	  *
	  * Equivalent to calling `send(message, data)` with Unit as data.
	  *
	  * @param message the message to send
	  */
	def send(message: String): Unit = send(message, ())

	/**
	  * Sends a message to the server with some arguments.
	  *
	  * @param message the message to send
	  * @param data    argument to the message
	  * @tparam P the type of the arguments,
	  *           an implicit Pickler must exist for this type
	  */
	def send[P: Pickler](message: String, data: P): Unit = {
		withChannel { chan => Future.successful(chan.send(message, data)) }
	}

	/**
	  * Sends a request to the server.
	  *
	  * This overload does not accept arguments and will send Unit as a subsitute.
	  * It is not possible to perform a silent request with this overload. If required,
	  * the same behavior can be obtained by calling `request(req, data)` with `()` as
	  * data.
	  *
	  * @param req the request key
	  * @return a Pending request that will be executed when a callback is defined or a
	  *         concrete, unpickled, type is specified with the .as[T] method.
	  */
	def request(req: String): PendingRequest[Unit] = request(req, (), false)

	/**
	  * Sends a request to the server with some arguments.
	  *
	  * @param req    the request key
	  * @param data   arguments to the request
	  * @param silent if true, the request will not trigger the activity indicator
	  * @tparam P the type of data sent, an implicit Pickler must exists for this type
	  * @return a PendingRequest that will be executed when a callback is defined or a
	  *         concrete, unpickled, type is specified with the .as[T] method.
	  */
	def request[P](req: String, data: P, silent: Boolean = false): PendingRequest[P] = {
		PendingRequest(req, data, silent)
	}

	/**
	  * A pending request ready to be sent.
	  *
	  * This class is used to move the implicit pickler out of the request call.
	  * This allow chaining a call to request without interfering with the implicit
	  * argument waiting to be given.
	  *
	  * In case the expected return type is Future[PickledPayload], the implicit
	  * conversion defined in the companion object kicks-in and ensure that the
	  * request is properly sent.
	  *
	  * Most of the time however, client code will either call .as[T] or one of the
	  * .apply methods.
	  *
	  * @param req    capture of the request action
	  * @param data   capture of the request data
	  * @param silent capture of the silent flag of the request
	  * @tparam P the type of the payload sent to the server
	  */
	case class PendingRequest[P](req: String, data: P, silent: Boolean) {
		/** Executes the request and return a future of its result */
		@inline def future(implicit p: Pickler[P]): Future[PickledPayload] = withChannel { chan =>
			chan.request(req, data, silent)
		}

		/** Executes the request and return a future of the requested type (after automatic unpickling) */
		@inline def as[T: Pickler](implicit p: Pickler[P]): Future[T] = future.map(_.as[T])

		/** Executes the request and forward the result to the given handler function */
		@inline def apply[T1: Pickler, R](fn: T1 => R)(implicit p: Pickler[P]): Future[R] = {
			future.map { pp => fn(pp.as[T1]) } andThen {
				case Failure(e) => console.error("Uncaught exception during message dispatch:\n\n" + App.formatException(e))
			}
		}

		/** Executes the request and forward the result to the given handler function */
		@inline def apply[T1: Pickler, T2: Pickler, R](fn: (T1, T2) => R)(implicit p: Pickler[P]): Future[R] = {
			apply(fn.tupled)
		}

		/** Executes the request and forward the result to the given handler function */
		@inline def apply[T1: Pickler, T2: Pickler, T3: Pickler, R](fn: (T1, T2, T3) => R)(implicit p: Pickler[P]): Future[R] = {
			apply(fn.tupled)
		}

		/** Executes the request and forward the result to the given handler function */
		@inline def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, R](fn: (T1, T2, T3, T4) => R)(implicit p: Pickler[P]): Future[R] = {
			apply(fn.tupled)
		}

		/** Executes the request and forward the result to the given handler function */
		@inline def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, T5: Pickler, R](fn: (T1, T2, T3, T4, T5) => R)(implicit p: Pickler[P]): Future[R] = {
			apply(fn.tupled)
		}
	}

	object PendingRequest {
		/** Automatically executes a pending request in case a Future[PickledPayload] is expected */
		@inline implicit def AutoExec[P](r: PendingRequest[P])(implicit p: Pickler[P]): Future[PickledPayload] = r.future
	}
}
