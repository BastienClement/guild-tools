package api

import java.lang.Exception
import actors.Actors._
import actors.SocketHandler
import play.api.libs.json.{JsValue, Json}
import gt.Global.ExecutionContext

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
				case ComposerLockoutUpdate(_) => true
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

		def handleLockoutRename = ComposerHandler { arg =>
			val id = (arg \ "lockout").as[Int]
			val title = (arg \ "title").as[String]
			ComposerService.renameLockout(id, title)
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
			val title = (arg \ "title").as[String]
			ComposerService.renameGroup(id, title)
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

		def handleExport = ComposerHandler { arg =>
			val groups = (arg \ "groups").as[List[Int]]
			val events = (arg \ "events").as[Set[Int]]
			val mode = (arg \ "mode").as[Int]
			val locked = (arg \ "locked").as[Boolean]
			ComposerService.exportGroups(groups, events, mode, locked) map {
				_ => MessageSuccess
			} recover {
				case err: Exception => MessageFailure(err.getMessage)
			}
		}
	}
}
