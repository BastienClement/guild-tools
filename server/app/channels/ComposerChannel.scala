package channels

import akka.actor.Props
import boopickle.DefaultBasic._
import gtp3.{ChannelHandler, ChannelRequest, ChannelValidator}
import models._
import models.composer.ComposerDocuments

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
}

