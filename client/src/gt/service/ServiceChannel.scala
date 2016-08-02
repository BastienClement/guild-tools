package gt.service

import boopickle.DefaultBasic._
import gt.{App, Server}
import gtp3.{Channel, PickledPayload}
import org.scalajs.dom.console
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}
import xuen.rx.{Rx, Var}

class ServiceChannel(tpe: String, lzy: Boolean)
                    (delegate: ((String, PickledPayload)) => Unit) {

	private[this] var channel: Channel = null
	private[this] val openStatus: Var[Boolean] = false

	val open: Rx[Boolean] = openStatus

	private[this] val queue = scalajs.js.Array[Channel => Unit]()
	private[this] var openPending = false

	private[service] def eagerOpen(): Unit = if (!lzy) lazyOpen()

	private[service] def lazyOpen(): Unit = {
		if (channel != null || openPending) return
		openPending = true

		Server.openChannel(tpe).andThen {
			case _ => openPending = false
		}.onComplete {
			case Success(chan) =>
				channel = chan
				openStatus := true

				chan.onMessage ~> delegate
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

	private[service] def close(): Unit = if (channel != null) {
		channel.close()
		channel = null
		openStatus := false
	}

	private def withChannel[T](action: Channel => Future[T]): Future[T] = {
		if (channel != null) action(channel)
		else {
			val promise = Promise[T]()
			queue.push(chan => promise.completeWith(action(chan)))
			lazyOpen()
			promise.future
		}
	}

	def send(message: String): Unit = send(message, ())
	def send[P: Pickler](message: String, data: P): Unit = {
		withChannel { chan => Future.successful(chan.send(message, data)) }
	}

	def request(req: String): Future[PickledPayload] = request(req, (), false)
	def request[P: Pickler](req: String, data: P, silent: Boolean = false): Future[PickledPayload] = {
		withChannel { chan => chan.request(req, data, silent) }
	}
}
