package channels.composer

import boopickle.DefaultBasic._
import gtp3.ChannelHandler
import models.User
import models.composer.{ComposerGroupSlot, ComposerGroupSlots, ComposerGroups}
import utils.SlickAPI._

class GroupDocumentChannel(user: User, doc: Int) extends ChannelHandler {
	init {
		ComposerGroups.subscribe()
		ComposerGroupSlots.subscribe()
	}

	akka {
		case ComposerGroups.Updated(grp) => send("group-updated", grp)
		case ComposerGroups.Deleted(id) => send("group-deleted", id)
		case ComposerGroupSlots.Updated(slot) => send("slot-updated", slot)
		case ComposerGroupSlots.Deleted(group, toon) => send("slot-deleted", (group, toon))
	}

	request("load-groups") {
		ComposerGroups.filter(_.doc === doc).run
	}

	message("create-group") { (doc: Int, title: String) =>
		ComposerGroups.create(doc, title)
	}

	message("delete-group") { (id: Int) =>
		ComposerGroups.delete(id)
	}

	request("load-slots") {
		ComposerGroupSlots.filter(_.group in ComposerGroups.filter(_.doc === doc).map(_.id)).run
	}

	message("set-slot") { (slot: ComposerGroupSlot) =>
		ComposerGroupSlots.set(slot)
	}

	message("unset-slot") { (group: Int, toon: Int) =>
		ComposerGroupSlots.unset(group, toon)
	}
}
