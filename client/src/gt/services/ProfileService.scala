package gt.services

import boopickle.DefaultBasic._
import gt.services.base.Service
import models.{Profile, Toon}
import scala.concurrent.Future

object ProfileService extends Service {
	val channel = registerChannel("profile")

	/**
	  * Checks if the requested toon is availble for registering.
	  *
	  * @param server the toon's server
	  * @param name   the toon's name
	  */
	def isToonAvailable(server: String, name: String): Future[Boolean] = {
		channel.request("toon-available", (server, name)).as[Boolean]
	}

	/**
	  * Fetches toon's data from the Battle.net API.
	  *
	  * @param server the toon's server
	  * @param name   the toon's name
	  */
	def fetchToon(server: String, name: String): Future[Toon] = {
		channel.request("fetch-toon", (server, name)).as[Toon]
	}

	/**
	  * Registers the given toon with the user account.
	  *
	  * @param server the toon's server
	  * @param name   the toon's name
	  * @param spec   the toon's registered spec
	  * @param owner  the toon's owner, setting this value to another value than
	  *               the current user's id requires promoted privileges.
	  */
	def registerToon(server: String, name: String, spec: Int, owner: Int): Future[Unit] = {
		channel.request("register-toon", (server, name, spec, owner)).as[Unit]
	}

	/**
	  * Load the requested user profile data.
	  *
	  * @param user the user id
	  */
	def userProfile(user: Int): Future[Profile] = {
		channel.request("user-profile", user).as[Profile]
	}
}
