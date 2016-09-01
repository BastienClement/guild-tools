package channels.composer

import akka.actor.Props
import gtp3.{ChannelRequest, ChannelValidator}
import models.composer.ComposerDocuments
import scala.concurrent.ExecutionContext.Implicits.global
import utils.SlickAPI._

object DocumentChannel extends ChannelValidator {
	def open(request: ChannelRequest) = {
		if (!request.user.promoted) request.reject(1, "Unauthorized")
		else {
			for (res <- ComposerDocuments.filter(_.id === request.token.toInt).headOption) res match {
				case Some(document) => documentHandlers(document.style)(request, document.id)
				case None => request.reject(2, "Document not found")
			}
		}
	}

	private val documentHandlers = Map(
		"GROUPS" -> (new GroupDocumentChannel(_, _)),
		"GRID" -> (new GridDocumentChannel(_, _))
	).mapValues { ctor =>
		(request: ChannelRequest, doc: Int) => request.accept(Props(ctor.apply(request.user, doc)))
	}.withDefaultValue {
		(request: ChannelRequest, doc: Int) => request.reject(3, "Unknown document type")
	}
}
