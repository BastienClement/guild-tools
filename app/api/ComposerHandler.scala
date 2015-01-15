package api

import actors.Actors._
import actors.SocketHandler
import play.api.libs.json.{JsValue, Json}

trait ComposerHandler {
	socket: SocketHandler =>

	private def ComposerHandler(body: (JsValue) => MessageResponse): (JsValue) => MessageResponse = {
		(arg: JsValue) => {
			if (!user.promoted) MessageFailure("You are not allowed to access the composer")
			else body(arg)
		}
	}

	private def ComposerHandler(body: => MessageResponse): (JsValue) => MessageResponse = {
		ComposerHandler { arg => body }
	}

	object Composer {
		def handleLoad = ComposerHandler {
			val (lockouts, groups, slots) = ComposerService.load

			bindEvents {
				case ComposerLockoutCreate(_) => true
				case ComposerLockoutDelete(_) => true
				case ComposerGroupCreate(_) => true
				case ComposerGroupUpdate(_) => true
				case ComposerGroupDelete(_) => true
				case ComposerSlotSet(_) => true
				case ComposerSlotUnset(_, _) => true
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

		def handleGroupRename = ComposerHandler { arg =>
			val id = (arg \ "group").as[Int]
			val name = (arg \ "name").as[String]
			ComposerService.renameGroup(id, name)
			MessageSuccess
		}

		def handleGroupDelete = ComposerHandler { arg =>
			val id = (arg \ "group").as[Int]
			ComposerService.deleteGroup(id)
			MessageSuccess
		}

		def handleSlotSet = ComposerHandler { arg =>
			val group = (arg \ "group").as[Int]
			val char = (arg \ "char").as[Int]
			val role = (arg \ "role").as[String]
			ComposerService.setSlot(group, char, role)
			MessageSuccess
		}

		def handleSlotUnset = ComposerHandler { arg =>
			val group = (arg \ "group").as[Int]
			val char = (arg \ "char").as[Int]
			ComposerService.unsetSlot(group, char)
			MessageSuccess
		}

		def handleExportGroup = ComposerHandler { arg =>
			val group = (arg \ "group").as[Int]
			val events = (arg \ "events").as[List[Int]]
			ComposerService.exportGroup(group, events) map {
				err => MessageFailure(err)
			} getOrElse MessageSuccess
		}
	}
}
