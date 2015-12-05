package controllers

import actors.StreamService
import com.google.inject.Inject
import models._
import models.live.Streams
import models.mysql._
import play.api.Play.current
import play.api.libs.ws.WSClient
import play.api.mvc.{Action, Controller}
import play.api.{Mode, Play}
import reactive.ExecutionContext
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.{Cache, CacheCell}

class Live @Inject() (ws: WSClient) extends Controller {
	private val client_stream = TrieMap[Int, String]()
	private val client_url = TrieMap[Int, String]()

	/**
	  * New connection to SRS
	  */
	def connect = Action { req =>
		val data = req.body.asJson.get
		client_url.put((data \ "client_id").as[Int], (data \ "tcUrl").as[String])
		Ok("0")
	}


	/**
	  * Disconnected from SRS
	  */
	def close = Action { req =>
		val data = req.body.asJson.get
		client_url.remove((data \ "client_id").as[Int])
		Ok("0")
	}

	/**
	  * Request to play a stream on SRS.
	  * Ensures that the request comes from the local server.
	  */
	def play = Action.async { req =>
		(for {
			data <- req.body.asJson
			app <- (data \ "app").asOpt[String] if app == "live"
			page <- (data \ "pageUrl").asOpt[String]
			client <- (data \ "client_id").asOpt[Int]
			remote <- (data \ "ip").asOpt[String]
		} yield (page, client, remote)) match {
			case None => Future.successful(Forbidden("1"))
			case Some((page, client, remote)) =>
				val ticket_id = page.substring(page.indexOf('?') + 1)
				(for (ticket <- StreamService.consumeTicket(ticket_id)) yield {
					StreamService.play(ticket.stream, ticket.user, remote, client)
					client_stream.put(client, ticket.stream)
					Ok("0")
				}) recover {
					case _ => Forbidden("1")
				}
		}
	}

	/**
	  * Disconnected from SRS.
	  * Nothing can be done in this callback, will be handled
	  * by the done() callback.
	  */
	def stop = Action { req =>
		(for {
			data <- req.body.asJson
			client <- (data \ "client_id").asOpt[Int]
		} yield client) match {
			case None => Forbidden("1")
			case Some(client) =>
				StreamService.stop(client_stream.remove(client).get, client)
				Ok("0")
		}
	}

	/**
	  * Beginning of a stream publishing.
	  */
	def publish = Action.async { req =>
		val data = req.body.asJson.get
		val token = (data \ "stream").as[String]
		val url = client_url((data \ "client_id").as[Int])
		"key=([a-zA-Z0-9]+)".r.findFirstMatchIn(url) match {
			case Some(matches) =>
				val key = matches.group(1)
				(for {
					stream <- Streams.filter(s => s.token === token && s.secret === key).head
				} yield {
					StreamService.publish(stream.token)
					Ok("0")
				}) recover {
					case _ => Forbidden("1")
				}

			case None =>
				Future.successful(Forbidden("1"))
		}
	}

	/**
	  * Ending of a stream publishing.
	  */
	def unpublish = Action { req =>
		val data = req.body.asJson.get
		val stream_id = (data \ "stream").as[String]
		StreamService.unpublish(stream_id)
		Ok("0")
	}

	/**
	  * Clappr iframe
	  */
	def clappr(stream: String) = Action {
		val host = Play.mode match {
			case Mode.Dev => "tv-dev.fs-guild.net"
			case Mode.Prod => "tv.fs-guild.net"
		}
		Ok(views.html.clappr.render(host, stream.replaceAll("[^a-zA-Z0-9]", "")))
	}

	// ------------------------------------------------------------------------------------------------------------------

	/**
	  * Fetch a file as a Array[Byte]
	  */
	private def fetch(url: String) = for {
		res <- ws.url(url).get()
		if res.status == 200
	} yield res.bodyAsBytes

	/**
	  * The clappr source file
	  */
	private val clappr_source = CacheCell.async(1.hour) {
		for {
			res <- ws.url("http://cdn.clappr.io/latest/clappr.min.js").get()
			if res.status == 200
		} yield res.body
	}

	/**
	  * Cache of the Clappr CDN
	  */
	private val clappr_cache = Cache.async[String, Array[Byte]](1.hour) {
		case "clappr.min.js" => clappr_source().map(_.getBytes())
		case name @ ("clappr.min.js.map" | "clappr.js") => fetch(s"http://cdn.clappr.io/latest/$name")
		case name =>
			for {
				clappr <- clappr_source()
				if name.matches("[0-9a-f]{32,40}\\.[0-9a-z]{2,3}") && clappr.indexOf("\"" + name + "\"") != -1
				res <- fetch(s"http://cdn.clappr.io/latest/$name")
			} yield res
	}

	/**
	  * Clappr CDN proxy
	  * This is required since there is no https version of cdn.clappr.io
	  */
	def clappr_proxy(file: String) = Action.async {
		clappr_cache(file) map {
			data => Ok(data)
		} recover {
			case e => InternalServerError
		}
	}
}
