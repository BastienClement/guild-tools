package controllers

import actors.StreamService
import models._
import models.live.Streams
import models.mysql._
import play.api.mvc.{Action, Controller}
import reactive.ExecutionContext
import scala.collection.concurrent.TrieMap
import scala.concurrent.Future

class Live extends Controller {
	val client_stream = TrieMap[String, String]()

	/**
	  * Consumes ticket and open stream proxy.
	  */
	def ticket = Action.async { req =>
		val infos = for {
			post <- req.body.asFormUrlEncoded
			stream_id <- post.get("name")
			remote <- post.get("addr")
			client <- post.get("clientid")
		} yield (stream_id.head, remote.head, client.head)

		infos match {
			case None => Future.successful(Forbidden("1"))
			case Some((stream_id, remote, client)) =>
				(for (ticket <- StreamService.consumeTicket(stream_id)) yield {
					StreamService.play(ticket.stream, ticket.user, remote, client)
					client_stream.put(client, ticket.stream)
					Redirect(s"rtmp://127.0.0.1/live/${ ticket.stream }")
				}) recover {
					case _ => Forbidden("1")
				}
		}
	}

	/**
	  * Stream proxy closed.
	  */
	def done = Action { req =>
		val infos = for {
			post <- req.body.asFormUrlEncoded
			stream_id <- post.get("name")
			remote <- post.get("addr")
			client <- post.get("clientid")
		} yield (stream_id.head, remote.head, client.head)

		infos match {
			case Some((stream_id, remote, client)) =>
				StreamService.stop(client_stream.remove(client).get, client)
			case None => // noop
		}

		Ok("")
	}

	/**
	  * Request to play a stream on SRS.
	  * Ensures that the request comes from the local server.
	  */
	def play = Action { req =>
		(for {
			data <- req.body.asJson
			ip <- (data \ "ip").asOpt[String] if ip == "127.0.0.1"
			app <- (data \ "app").asOpt[String] if app == "live"
		} yield ()) match {
			case Some(_) => Ok("0")
			case None => Forbidden("1")
		}
	}

	/**
	  * Disconnected from SRS.
	  * Nothing can be done in this callback, will be handled
	  * by the done() callback.
	  */
	def stop = Action { req =>
		Ok("0")
	}

	/**
	  * Beginning of a stream publishing.
	  */
	def publish = Action.async { req =>
		val data = req.body.asJson.get
		(for {
			stream <- Streams.filter(_.token === (data \ "stream").as[String]).head
		} yield {
			StreamService.publish(stream.token)
			Ok("0")
		}) recover {
			case _ => Forbidden("1")
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
}
