package gt.components.composer.groups

import gt.components.GtHandler
import gt.components.widget.form.{GtButton, GtForm, GtInput}
import gt.components.widget.{GtBox, GtDialog}
import gt.services.ComposerService
import models.composer.ComposerDocument
import utils.jsannotation.js
import xuen.Component

object ComposerGroupsDocument extends Component[ComposerGroupsDocument](
	selector = "composer-groups-document",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox, GtDialog, GtForm, GtInput, GtButton)
)

@js class ComposerGroupsDocument extends GtHandler {
	val composer = service(ComposerService)
	val doc = property[ComposerDocument]

	def renameDocument(title: String): Unit = composer.renameDocument(doc.id, title)
}

