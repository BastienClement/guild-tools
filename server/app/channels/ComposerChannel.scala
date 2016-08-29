package channels

import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3.{ChannelHandler, ChannelRequest, ChannelValidator}
import models.composer.ComposerDocuments
import utils.SlickAPI._

object ComposerChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (request.user.promoted) request.accept(Props(new ComposerChannel))
		else request.reject(1, "Unauthorized")
	}
}

class ComposerChannel extends ChannelHandler {
	init {
		ComposerDocuments.subscribe()
	}

	akka {
		case ComposerDocuments.Updated(doc) => send("document-updated", doc)
		case ComposerDocuments.Deleted(id) => send("document-deleted", id)
	}

	request("load-documents") {
		ComposerDocuments.run
	}

	message("create-document") { (title: String, style: String) =>
		ComposerDocuments.create(title, style)
	}

	message("rename-document") { (id: Int, name: String) =>
		ComposerDocuments.rename(id, name)
	}
}

