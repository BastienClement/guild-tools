package controllers

import akka.actor.Props
import gtp3.WSActor
import play.api.Play.current
import play.api.mvc.{Action, Controller, WebSocket}

object Application extends Controller {
	def catchall(path: String) = client

	def client = Action {
		Ok(views.html.client.render())
	}

	def gtp3 = WebSocket.acceptWithActor[Array[Byte], Array[Byte]] { request => out =>
		Props(new WSActor(out, request))
	}

	def unsupported = Action {
		Ok(views.html.unsupported.render())
	}
}
