package channels

import actors.{BattleNet, RosterService}
import akka.actor.Props
import gtp3._
import models._
import models.mysql._
import reactive._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
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

	// Last fetched char
	private var last_char: Option[Char] = None

	// Construct rate limited function to prevent battle.net API flooding
	private def rateLimited[T, U](fn: (T) => Future[U]): (T) => Future[U] = p => {
		bucket = Math.min(bucket + ((Platform.currentTime - last) / 1000) * (limit / interval), limit)
		last = Platform.currentTime
		if (bucket >= 1.0) {
			bucket -= 1.0
			fn(p)
		} else {
			Future.failed(Error("Please wait a moment before requesting too many characters from Battle.net", 12))
		}
	}

	// Fetch a char from Battle.net
	private val fetchChar = rateLimited[(String, String), Char]{ case (s, n) => BattleNet.fetchChar(s, n) }

	// Fetch a char from Battle.net
	request("fetch-char")(rateLimited { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]
		fetchChar(server, name) andThen {
			case Success(char) => last_char = char
		}
	})

	// Check if a specific character is registered in the database
	request("is-char-available") { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]

		for {
			// Check that server is valid
			realms <- Profile.realms_cache.value
			exists = realms.exists { case (a, b) => a == server }
			_ = if (exists) () else throw new Exception("Invalid server name")

			// Query local database
			query = Chars.filter(c => c.server === server && c.name === name).size === 0
			free <- query.result.run
		} yield free
	}

	// Fetch the realms list from the database
	request("realms-list") { _ => Profile.realms_cache.value }

	// Register a new char to a user
	request("register-char") { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]
		val role = p("role").asOpt[String].filter(Chars.validateRole)
		last_char match {
			case Some(char) if char.server == server && char.name == name =>
				RosterService.addChar(char, user, role)
			case _ =>
				RosterService.addChar(server, name, user, role)
		}
	}
}
