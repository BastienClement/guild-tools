package controllers

import akka.actor.Props
import akka.stream.Materializer
import com.google.inject.Inject
import gt.GuildTools
import gtp3.WSActor
import java.util.UUID
import play.api.libs.streams.ActorFlow
import play.api.libs.ws.{WSAuthScheme, WSClient}
import play.api.mvc.{Action, Controller, WebSocket}
import play.api.{Configuration, Environment}
import scala.concurrent.{ExecutionContext, Future}

class GtApplication @Inject() (ws: WSClient, conf: Configuration)
                              (implicit val mat: Materializer, val env: Environment, ec: ExecutionContext)
		extends Controller {
	implicit val ac = GuildTools.system

	def sanitizeSession(session: String) = session.replaceAll("[^a-zA-Z0-9\\-_\\.]", "")

	def client = Action { req =>
		val state = UUID.randomUUID.toString
		Ok(views.html.client.render(state, req.flash.get("token"), env)).withSession("state" -> state)
	}

	def gtp3 = WebSocket.accept[Array[Byte], Array[Byte]] { request =>
		ActorFlow.actorRef { out => Props(new WSActor(out, request)) }
	}

	def oauth = Action.async { req =>
		val Array(nonce, continue) = req.getQueryString("state").map(_.split(":", 2)).get
		if (!req.session.get("state").contains(nonce) || req.getQueryString("code").isEmpty) {
			Future.successful(Redirect("/unauthorized"))
		} else {
			val username = conf.getString("oauth.client").get
			val password = conf.getString("oauth.secret").get
			ws.url("https://auth.fromscratch.gg/oauth/token").withAuth(username, password, WSAuthScheme.BASIC).post(Map(
				"grant_type" -> Seq("authorization_code"),
				"code" -> Seq(req.getQueryString("code").get)
			)).map { response =>
				Redirect(continue).flashing("token" -> (response.json \ "access_token").as[String])
			}.recover {
				case _ => Redirect("/unauthorized")
			}
		}
	}

	def catchall(path: String) = {
		if (path == "favicon.ico") Action { NotFound }
		else client
	}
	def unauthorized = Action { Ok(views.html.unauthorized.render()) }
	def unsupported = Action { Ok(views.html.unsupported.render()) }

	def broken_path(path: String) = broken
	def broken = Action { Ok(views.html.broken.render()) }
}
