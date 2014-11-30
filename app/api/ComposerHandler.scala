package api

import actors.Actors._
import actors.SocketHandler
import play.api.libs.json.{JsValue, Json}

trait ComposerHandler {
	socket: SocketHandler =>

	private def ComposerHandler(body: (JsValue) => MessageResponse): (JsValue) => MessageResponse = {
		(arg: JsValue) => {
			if (!user.officer) MessageFailure("You are not allowed to access the composer")
			else body(arg)
		}
	}

	private def ComposerHandler(body: => MessageResponse): (JsValue) => MessageResponse = {
		ComposerHandler { arg =>
			body
		}
	}

	object Composer {
		def handleLoad = ComposerHandler {
			val (lockouts, groups, slots) = ComposerService.load

			bindEvents {
				case ComposerLockoutCreate(_) => true
				case ComposerLockoutDelete(_) => true
				case ComposerGroupCreate(_) => true
				case ComposerGroupDelete(_) => true
			}

			Json.obj("lockouts" -> lockouts, "groups" -> groups, "slots" -> slots)
		}

		def handleLockoutCreate = ComposerHandler { arg =>
			val title = (arg \ "title").as[String]
			ComposerService.createLockout(title)
			MessageSuccess
		}

		def handleLockoutDelete = ComposerHandler { arg =>
			val id = (arg \ "lockout").as[Int]
			ComposerService.deleteLockout(id)
			MessageSuccess
		}

		def handleGroupCreate = ComposerHandler { arg =>
			val lockout = (arg \ "lockout").as[Int]
			ComposerService.createGroup(lockout)
			MessageSuccess
		}

		def handleGroupDelete = ComposerHandler { arg =>
			val id = (arg \ "group").as[Int]
			ComposerService.deleteGroup(id)
			MessageSuccess
		}
	}
}
