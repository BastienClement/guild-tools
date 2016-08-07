package channels

import actors.{BattleNet, RosterService}
import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3.{Error, _}
import model.{Toon, User}
import models._
import models.mysql._
import reactive._
import scala.compat.Platform
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Success
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
	private var lastToon: Option[Toon] = None

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

	// Fetch a toon from Battle.net
	private val fetchToon = rateLimited[(String, String), Toon]{ case (s, n) => BattleNet.fetchToon(s, n) }

	// Fetch a toon from Battle.net
	request("fetch-toon") { (server: String, name: String) =>
		fetchToon(server, name) andThen {
			case Success(char) => lastToon = Some(char)
		}
	}

	// Check if a specific character is registered in the database
	request("toon-available") { (server: String, name: String) =>
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
	request("register-toon") { (server: String, name: String, role: String, owner: Int) =>
		require(Toons.validateRole(role))
		val effectiveOwner = if (user.promoted) owner else user.id

		lastToon match {
			case Some(toon) if toon.server == server && toon.name == name =>
				RosterService.registerChar(toon, effectiveOwner, Some(role))
			case _ =>
				RosterService.registerChar(server, name, effectiveOwner, Some(role))
		}
	}

	request("user-profile") { id: Int =>
		Profiles.filter(_.user === id).head.map { data =>
			data.concealFor(user)
		}.recover { case _ =>
			models.Profile(id, None, None, None, None, None, None)
		}
	}
}
