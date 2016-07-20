package gt

import scala.concurrent.Future
import util.Global._

/** Manages connection to the server */
object Server {
	/** Connects to the server */
	def connect(url: String): Future[Unit] = {
		println("Connect", url)
		println(normalizeURL(url))
		Future.successful(())
	}

	/** Normalizes the WebSocket url */
	private def normalizeURL(start: String): String = {
		var url = start
		if (document.location.protocol == "https:")
			url = url.replaceFirst("^ws:", "wss:")
		for (key <- Seq("hostname", "port", "host"))
			url = url.replace("$" + key, dynamic.location.selectDynamic(key).asInstanceOf[String])
		url
	}
}
