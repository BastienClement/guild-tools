package gt.service

import boopickle.DefaultBasic._
import gt.service.base.Service
import model.Toon
import scala.concurrent.Future

object Profile extends Service {
	val channel = registerChannel("profile")

	def isToonAvailable(server: String, name: String): Future[Boolean] = {
		channel.request("toon-available", (server, name)).as[Boolean]
	}

	def fetchToon(server: String, name: String): Future[Toon] = {
		channel.request("fetch-toon", (server, name)).as[Toon]
	}

	def registerToon(server: String, name: String, role: String, owner: Int): Future[Unit] = {
		channel.request("register-toon", (server, name, role, owner)).as[Unit]
	}
}
