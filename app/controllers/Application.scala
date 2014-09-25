package controllers

import play.api.Play.current
import play.api.libs.json.JsValue
import play.api.mvc.{ Action, Controller, WebSocket }

import actors.SocketHandler
import akka.actor.Props

object Application extends Controller {
	def index() = Action {
		Ok(views.html.index.render())
	}

	def catchall(path: String) = index()

	def socket = WebSocket.acceptWithActor[JsValue, JsValue] { request =>
		out =>
			Props(new SocketHandler(out, request.remoteAddress))
	}

	/*def test = Action {
		/*DB.withConnection { implicit c =>
			val res = SQL("SELECT * FROM phpbb_users WHERE username = {user}").on("user" -> "Blash").apply().head
			Ok(res[Long]("user_id").toString)
		}*/
	}*/
}
