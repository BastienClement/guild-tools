package actors

import scala.concurrent.Future
import actors.Actors.Implicits._
import models._
import models.mysql._

trait SessionManagerInterface {
	def userForSession(id: String): Future[User]
}

class SessionManager extends SessionManagerInterface {
	var onlines = Map[Int, User]

	def userById(id: Int): Future[User] = {
		None
	}

	def userForSession(id: String): Future[User] = {
		DB.withSession { implicit s =>
			val user = for (s <- Sessions if s.token === id) yield s.user
			user.firstOption flatMap (userById(_))
		}
	}
}
