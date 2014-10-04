package api

import actors.SocketHandler
import gt.User
import play.api.libs.json.{ JsString, JsValue }

trait ProfileHandler { this: SocketHandler =>
	/**
	 * $:profile:load
	 */
	def handleProfileLoad(arg: JsValue): MessageResponse = {
		val user_id = (arg \ "id").as[Int]
		User.findByID(user_id) map { user =>
			MessageResults(JsString(user.name))
		} getOrElse {
			MessageFailure("PROFILE_NOT_FOUND")
		}
	}
}
