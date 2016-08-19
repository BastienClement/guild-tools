package gt

import gtp3.{Channel, GTP3Error, Socket}
import rx.Var
import scala.concurrent.{Future, Promise}
import scala.scalajs.js.timers.setTimeout
import util.Settings

/** Manages connection to the server */
object Server {
	private[this] var promise: Promise[Unit] = null
	private[this] var socket: Socket = null

	private[this] var requestCount = 0
	private[this] var loadingTransition = false

	val version = Var[String](null)
	val loading = Var[Boolean](false)

	/** Connects to the server */
	def connect(url: String): Future[_] = {
		promise = Promise[Unit]()
		socket = new Socket(url)

		socket.verbose = Settings.`socket.verbose`

		socket.onConnect ~> `on-connect`
		socket.onClose ~> `on-close`
		socket.onReconnect ~> `on-reconnect`
		socket.onReset ~> `on-reset`
		socket.onRequestStart ~> `on-request-start`
		socket.onRequestEnd ~> `on-request-end`

		socket.connect()

		setTimeout(5000) {
			if (promise != null) {
				promise.failure(GTP3Error("Socket connection timed out"))
				promise = null
			}
		}

		promise.future
	}

	/** EVENT: The socket is connected to the server */
	private def `on-connect`(serverVersion: String): Unit = {
		for (app <- App.root) app.connected()
		version := serverVersion
		if (promise != null) promise.success(())
		promise = null
	}

	private def `on-reconnect`(): Unit = {
		for (app <- App.root) app.reconnecting()
	}

	/** EVENT: The socket is definitively closed */
	private def `on-close`(reason: String): Unit = {
		for (app <- App.root) app.disconnected()
		if (promise != null) {
			promise.failure(GTP3Error("Socket was closed"))
			promise = null
		}
	}

	/** EVENT: Connection with the server was re-established but the session was lost */
	private def `on-reset`(): Unit = {
		for (app <- App.root) app.reset()
		// There is no way to do that properly on GT6, we'll need to reload the whole app
		// Modular architecture is too complex and stateful design cannot be restored properly
		socket.close()
	}

	/** EVENT: A request started */
	private def `on-request-start`(): Unit = {
		requestCount += 1
		if (requestCount > 0) {
			updateLoading(true)
		}
	}

	/** A request is completed */
	private def `on-request-end`(): Unit = {
		requestCount -= 1
		if (requestCount < 1) {
			updateLoading(false)
		}
	}

	/**
	  * Update the loading status.
	  * Enforce a delay on state changes to prevent flickering
	  *
	  * @param state the new loading state
	  */
	private def updateLoading(state: Boolean): Unit = if (!loadingTransition) {
		loadingTransition = true

		// Update the loading state
		loading := state

		// Lockout
		setTimeout(if (state) 1500 else 500) {
			loadingTransition = false

			// Ensure that the loading state is still valid
			if (state && requestCount < 1) updateLoading(false)
			else if (!state && requestCount > 0) updateLoading(true)
		}
	}

	/** Request to open a channel */
	def openChannel(tpe: String, token: String = ""): Future[Channel] = socket.openChannel(tpe, token)
}
