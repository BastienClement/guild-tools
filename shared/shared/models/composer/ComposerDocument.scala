package models.composer

import boopickle.DefaultBasic._
import utils.annotation.data

@data case class ComposerDocument(id: Int, title: String, style: String) {
	require(style == DocumentStyle.Grid || style == DocumentStyle.Groups)
	@inline final def dummy = id < 1
}

object ComposerDocument {
	implicit val ComposerDocumentPickler = PicklerGenerator.generatePickler[ComposerDocument]
}
