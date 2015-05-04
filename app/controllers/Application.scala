package controllers

import actors.{GTP3Socket, CompressedSocketHandler, SocketHandler}
import akka.actor.Props
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

	def socket = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
		Props(new SocketHandler(out, request.remoteAddress))
	}

	def socket_z = WebSocket.acceptWithActor[Array[Byte], Array[Byte]] { request => out =>
		Props(new CompressedSocketHandler(out, request.remoteAddress))
	}

	def gtp3 = WebSocket.acceptWithActor[Array[Byte], Array[Byte]] { request => out =>
		Props(new GTP3Socket(out, request))
	}

	def test = Action {
		Ok("")
	}
}
