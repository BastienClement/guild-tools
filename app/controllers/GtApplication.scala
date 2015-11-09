package controllers

import akka.actor.Props
import com.google.inject.Inject
import gt.GuildTools
import gtp3.WSActor
import play.api.Play.current
import play.api.mvc.{Action, Controller, WebSocket}

class GtApplication @Inject() (gt: GuildTools) extends Controller {
	def client = Action { Ok(views.html.client.render()) }
	def catchall(path: String) = client

	def gtp3 = WebSocket.acceptWithActor[Array[Byte], Array[Byte]] { request => out =>
		Props(new WSActor(out, request))
	}

	def unsupported = Action { Ok(views.html.unsupported.render()) }

	def clappr(ticket: String) = Action { Ok(views.html.clappr.render(ticket.replaceAll("[^a-zA-Z0-9]", ""))) }
}
