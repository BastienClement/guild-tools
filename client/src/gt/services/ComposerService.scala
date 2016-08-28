package gt.services

import boopickle.DefaultBasic._
import gt.services.base.{Cache, Delegate, Service}
import models.composer.ComposerDocument

object ComposerService extends Service with Delegate {
	private val channel = registerChannel("composer")

	object documents extends Cache((doc: ComposerDocument) => doc.id) {

	}

	def createDocument(title: String, style: String): Unit = channel.send("create-document", (title, style))

	message("document-updated")(documents.update _)
	message("document-removed")(documents.removeKey _)

	override protected def enable(): Unit = {
		channel.request("load-documents") { (docs: Seq[ComposerDocument]) =>
			docs.foreach(documents.update)
		}
	}

	override protected def disable(): Unit = {
		documents.clear()
	}
}
