package gt.components.composer

import gt.Router
import gt.components.widget.form._
import gt.components.widget.{GtBox, GtDialog}
import gt.components.{GtHandler, Tab, View}
import gt.services.ComposerService
import models.User
import models.composer.ComposerDocument
import rx.{Const, Rx, Var}
import utils.Lazy
import utils.jsannotation.js
import xuen.Component

/**
  * The composer view.
  */
object GtComposer extends Component[GtComposer](
	selector = "gt-composer",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox, GtButton, GtDialog, GtForm, GtInput, GtCheckbox, GtCheckboxGroup)
) with View.Sticky {
	val module = "composer"

	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Composer", "/composer", true)
	)

	override def validate(user: User): Boolean = user.promoted

	private val DefaultDocumentRx: Rx[ComposerDocument] = Const(null)
}

@js class GtComposer extends GtHandler {
	private val composer = service(ComposerService)

	val docid = attribute[String] ~> updateDocument _

	private val docProvider = Var[Rx[ComposerDocument]](GtComposer.DefaultDocumentRx)
	def doc = docProvider.!
	def hasDoc = null != doc.!

	private def updateDocument(docid: String): Unit = {
		if (docid == null) docProvider := GtComposer.DefaultDocumentRx
		else {
			val id = docid.toInt
			composer.documents.contains(id) match {
				case true => docProvider := composer.documents.get(id)
				case false => Router.goto("/composer")
			}
		}
	}

	val createDialog = Lazy(child.as[GtDialog]("#create-document"))
	val createTitle = Var[String]
	val createStyle = Var[String]
	def canCreate = Option(createTitle.!).exists(_.nonEmpty)

	def showCreate(): Unit = {
		createTitle := ""
		createStyle := "GROUPS"
		createDialog.show()
	}

	def createDocument(): Unit = if(canCreate) {
		composer.createDocument(createTitle, createStyle)
		createDialog.hide()
	}
}
