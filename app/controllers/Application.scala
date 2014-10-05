package controllers

import actors.SocketHandler
import akka.actor.Props
import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc.{Action, Controller, WebSocket}
import models._
import models.mysql._
import gt.Bnet

object Application extends Controller {
	def index() = Action {
		Ok(views.html.index.render())
	}

	def catchall(path: String) = index()

	def socket = WebSocket.acceptWithActor[JsValue, JsValue] { request =>
		out =>
			Props(new SocketHandler(out, request.remoteAddress))
	}
	
	def test = Action {
		//Ok(Bnet.query("/character/illidan/Blãsh", ("fields" -> "items")))
		Ok("")
	}
}
