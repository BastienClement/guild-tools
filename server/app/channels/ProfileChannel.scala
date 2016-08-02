package channels

import actors.BattleNet
import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3._
import model.{Toon, User}
import models._
import models.mysql._
import reactive._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import utils.CacheCell

object ProfileChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new ProfileChannel(request.user)))

	// Cache of every realms in the database
	private val realms_cache = CacheCell.async(15.minutes) {
		sql"SELECT slug, name FROM gt_realms".as[(String, String)].run
	}
}

class ProfileChannel(val user: User) extends ChannelHandler {
	// Configuration for B.net rate limitation
	private val limit = 4.0
	private val interval = 30.0
	private var bucket = limit
	private var last = Platform.currentTime

	// Last fetched char
	private var last_char: Option[Toon] = None

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
	private val fetchChar = rateLimited[(String, String), Toon]{ case (s, n) => BattleNet.fetchChar(s, n) }

	// Fetch a char from Battle.net
	/*request("fetch-char")(rateLimited { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]
		fetchChar(server, name) andThen {
			case Success(char) => last_char = char
		}
	})*/

	// Check if a specific character is registered in the database
	request("is-char-available") { (server: String, name: String) =>
		for {
			// Check that server is valid
			realms <- ProfileChannel.realms_cache.value
			exists = realms.exists { case (a, b) => a == server }
			_ = if (exists) () else throw new Exception("Invalid server name")

			// Query local database
			query = Toons.filter(c => c.server === server && c.name === name).size === 0
			free <- query.result.run
		} yield free
	}

	// Fetch the realms list from the database
	request("realms-list") { ProfileChannel.realms_cache.value }

	// Register a new char to a user
	/*request("register-char") { p =>
		val server = p("server").as[String]
		val name = p("name").as[String]
		val role = p("role").asOpt[String].filter(Toons.validateRole)
		val owner = if (user.promoted) p("owner").asOpt[Int].getOrElse(user.id) else user.id

		last_char match {
			case Some(char) if char.server == server && char.name == name =>
				RosterService.registerChar(char, owner, role)
			case _ =>
				RosterService.registerChar(server, name, owner, role)
		}
	}*/

	request("user-profile") { id: Int =>
		Profiles.filter(_.user === id).head.map { data =>
			data.concealFor(user)
		}.recover { case _ =>
			models.Profile(id, None, None, None, None, None, None)
		}
	}
}
