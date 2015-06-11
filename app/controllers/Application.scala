package controllers

import actors.{CompressedSocketHandler, SocketHandler}
import akka.actor.Props
import gtp3.SocketActor
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc.{Action, Controller, WebSocket}

object Application extends Controller {
	def catchall(path: String) = index()

	def index() = Action {
		Ok(views.html.index.render())
	}

	def client() = Action {
		Ok(views.html.client.render())
	}

	def gtp3 = WebSocket.acceptWithActor[Array[Byte], Array[Byte]] { request => out =>
		Props(new SocketActor(out, request.remoteAddress))
	}

	def test = Action {
		Ok("")
	}
}
