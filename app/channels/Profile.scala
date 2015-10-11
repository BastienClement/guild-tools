package channels

import actors.Actors._
import akka.actor.Props
import gtp3.ChannelHandler.FuturePayloadBuilder
import gtp3._
import models._
import models.mysql._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.CacheCell

object Profile extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new Profile(request.user)))

	// Cache of every realms in the database
	private val realms_cache = CacheCell.async(15.minutes) {
		sql"SELECT slug, name FROM gt_realms".as[(String, String)].run
	}
}

class Profile(val user: User) extends ChannelHandler {
	// Configuration for B.net rate limitation
	private val limit = 4.0
	private val interval = 30.0
	private var bucket = limit
	private var last = Platform.currentTime

	// Construct rate limited function to prevent battle.net API flooding
	private def rateLimited[T](fn: (Payload) => T)(implicit fpb: FuturePayloadBuilder[T]): (Payload) => Future[Payload] = p => {
		bucket = Math.min(bucket + ((Platform.currentTime - last) / 1000) * (limit / interval), limit)
		last = Platform.currentTime
		if (bucket >= 1.0) {
			bucket -= 1.0
			fpb.build(fn(p))
		} else {
			Future.failed(Error("Please wait a moment before requesting too many characters from Battle.net", 12))
		}
	}

	// Fetch a char from Battle.net
	request("fetch-char")(rateLimited { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]
		BattleNet.fetchChar(server, name)
	})

	// Check if a specific character is registered in the database
	request("is-char-available") { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]
		val free = Chars.filter(c => c.server === server && c.name === name).size === 0
		free.result.run
	}

	// Fetch the realms list from the database
	request("realms-list") { _ => Profile.realms_cache.value }
}
