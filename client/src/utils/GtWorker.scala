package utils

import org.scalajs.dom.webworkers.Worker
import org.scalajs.dom.{ErrorEvent, MessageEvent}
import scala.collection.mutable
import scala.concurrent.{Future, Promise}
import scala.scalajs.js
import utils.Global._

object GtWorker {
	private val instances = mutable.Map.empty[String, GtWorker]
	def singleton(path: String) = instances.getOrElseUpdate(path, new GtWorker(path))
}

/**
  * Frontend for a Worker using the GtWorker protocol.
  *
  * @param path the worker script path
  */
class GtWorker(path: String) {
	private val worker = new Worker(path)
	private var nextRID = 0
	private val requests = mutable.Map.empty[Int, Promise[js.Any]]

	worker.onmessage = messageHandler _
	worker.onerror = errorHandler _

	private def messageHandler(m: MessageEvent) = {
		val data = m.data.asInstanceOf[js.Dynamic]

		if (data.$.asInstanceOf[String] == "res") {
			val rid = data.rid.asInstanceOf[Int]
			requests.get(rid) match {
				case Some(promise) =>
					requests.remove(rid)
					promise.success(data.res)

				case None =>
					console.error("Undefined request", data)
			}
		} else {
			console.error("Undefined message", data)
		}
	}

	private def errorHandler(e: ErrorEvent) = {
		console.error(e)
	}

	/** Performs a request to the service worker */
	def request[A](method: String, args: js.Any*): Future[A] = {
		val promise = Promise[A]()
		val rid = nextRID
		nextRID += 1

		requests.put(rid, promise.asInstanceOf[Promise[js.Any]])

		worker.postMessage(js.Dynamic.literal(
			"$" -> method,
			"rid" -> rid,
			"args" -> js.Array(args: _*)
		))

		promise.future
	}
}
