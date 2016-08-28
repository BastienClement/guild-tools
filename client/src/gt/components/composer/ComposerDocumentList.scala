package gt.components.composer

import gt.Router
import gt.components.GtHandler
import gt.components.widget.GtBox
import gt.services.ComposerService
import utils.jsannotation.js
import xuen.Component

/**
  * The composer view.
  */
object ComposerDocumentList extends Component[ComposerDocumentList](
	selector = "composer-document-list",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox)
)

@js class ComposerDocumentList extends GtHandler {
	private val composer = service(ComposerService)
	val docs = composer.documents.values ~ (_.iterator.map(_.!).toSeq.sortWith((a, b) => a.id > b.id))

	def selectDocument(id: Int): Unit = Router.goto(s"/composer/$id")
}
