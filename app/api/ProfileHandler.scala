package api

import actors.SocketHandler
import gt.User
import play.api.libs.json.{ JsString, JsValue }

trait ProfileHandler { this: SocketHandler =>
	def handleUserLoad(arg: JsValue): MessageResponse = {
		val user_id = (arg \ "id").as[Int]
		User.findByID(user_id) map { user =>
			MessageResults(JsString(user.name))
		} getOrElse {
			MessageFailure("USER_NOT_FOUND")
		}
	}
}
