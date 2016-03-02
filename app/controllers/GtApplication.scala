package controllers

import akka.actor.Props
import com.google.inject.Inject
import gt.GuildTools
import gtp3.WSActor
import play.api.Play.current
import play.api.mvc.{Action, Controller, WebSocket}

class GtApplication @Inject() (gt: GuildTools) extends Controller {
	def sanitizeSession(session: String) = session.replaceAll("[^a-z0-9]", "")

	def client = Action { req =>
		Ok(views.html.client.render(req.flash.get("session").map(sanitizeSession)))
	}


	def gtp3 = WebSocket.acceptWithActor[Array[Byte], Array[Byte]] { request => out =>
		Props(new WSActor(out, request))
	}

	def sso = Action { req =>
		val session = req.getQueryString("session").map(sanitizeSession).getOrElse("")
		val target = req.getQueryString("token").getOrElse("/").replaceAll("('|\\\\)", "").trim match {
			case "" => "/"
			case str => str
		}
		Redirect(target).flashing("session" -> session)
	}

	def catchall(path: String) = client
	def broken(path: String) = Action { Ok(views.html.broken.render()) }
	def unauthorized = Action { Ok(views.html.unauthorized.render()) }
	def unsupported = Action { Ok(views.html.unsupported.render()) }
}
