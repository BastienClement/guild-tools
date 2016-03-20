package controllers

import akka.actor.Props
import akka.stream.Materializer
import com.google.inject.Inject
import gt.GuildTools
import gtp3.WSActor
import play.api.libs.streams.ActorFlow
import play.api.mvc.{Action, Controller, WebSocket}

class GtApplication @Inject() (implicit val mat: Materializer) extends Controller {
	implicit val ac = GuildTools.system

	def sanitizeSession(session: String) = session.replaceAll("[^a-z0-9]", "")

	def client = Action { req =>
		Ok(views.html.client.render(req.flash.get("session").map(sanitizeSession)))
	}

	def gtp3 = WebSocket.accept[Array[Byte], Array[Byte]] { request =>
		ActorFlow.actorRef { out => Props(new WSActor(out, request)) }
	}

	def sso = Action { req =>
		val session = req.getQueryString("session").map(sanitizeSession).getOrElse("")
		val target = req.getQueryString("token").getOrElse("/").replaceAll("('|\\\\)", "").trim match {
			case "" => "/"
			case str => str
		}

		if (session == "" && req.getQueryString("logout").isEmpty) Redirect("/unauthorized")
		else Redirect(target).flashing("session" -> session)
	}

	def catchall(path: String) = client
	def unauthorized = Action { Ok(views.html.unauthorized.render()) }
	def unsupported = Action { Ok(views.html.unsupported.render()) }

	def broken_path(path: String) = broken
	def broken = Action { Ok(views.html.broken.render()) }
}
