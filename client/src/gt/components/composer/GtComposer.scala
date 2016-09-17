package gt.components.composer

import gt.Router
import gt.components.calendar.CalendarUnitFrame
import gt.components.composer.groups.ComposerGroupsDocument
import gt.components.widget.form._
import gt.components.widget.{GtBox, GtDialog}
import gt.components.{GtHandler, Tab, View}
import gt.services.ComposerService
import models.User
import models.composer.{ComposerDocument, DocumentStyle}
import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import org.scalajs.dom.raw.HTMLDivElement
import rx.{Const, Rx, Var}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.scalajs.js
import utils.Lazy
import utils.jsannotation.js
import xuen.Component

/**
  * The composer view.
  */
object GtComposer extends Component[GtComposer](
	selector = "gt-composer",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox, GtButton, GtDialog, GtForm, GtInput, GtCheckbox, GtCheckboxGroup,
		ComposerDocumentList, ComposerRoster, ComposerGroupsDocument, CalendarUnitFrame)
) with View.Sticky {
	val module = "composer"

	val tabs: TabGenerator = (selector, path, user) => Seq(
		Tab("Composer", "/composer", true)
	)

	override def validate(user: User): Boolean = user.promoted

	private val DummyDocument = ComposerDocument(-1, "", DocumentStyle.Groups)
	private val DefaultDocumentRx: Rx[ComposerDocument] = Const(DummyDocument)
}

@js class GtComposer extends GtHandler {
	private val composer = service(ComposerService)

	val docid = attribute[String] ~> updateDocument _

	private val docProvider = Var[Rx[ComposerDocument]](GtComposer.DefaultDocumentRx)
	def doc = docProvider.!
	def hasDoc = !doc.dummy

	private def updateDocument(docid: String): Unit = {
		usedProvider := None
		for (_ <- composer.ready) {
			if (docid == null) docProvider := GtComposer.DefaultDocumentRx
			else {
				val id = docid.toInt
				composer.documents.contains(id) match {
					case true => docProvider := composer.documents.get(id)
					case false => Router.goto("/composer")
				}
			}
		}
	}

	val deleteListener = (key: Int) => {
		if (key == doc.id) Router.goto("/composer")
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

	def createDocument(): Unit = if (canCreate) {
		composer.createDocument(createTitle, createStyle)
		createDialog.hide()
	}

	val filterRoster = Var[Boolean] := true
	val filterCasuals = Var[Boolean]
	val filterVeterans = Var[Boolean]
	val filterGuests = Var[Boolean]
	def filter = ComposerRoster.Filter(filterRoster, filterCasuals, filterVeterans, filterGuests)

	override def attached(): Unit = {
		composer.documentDeleted ~> deleteListener
	}

	override def detached(): Unit = {
		composer.documentDeleted ~/> deleteListener
	}

	//
	// Drag and drop management
	//
	val hasPicked = Var(false)
	val picked = Var[Int]
	val dragItem = Lazy(child.as[HTMLDivElement]("#drag-item"))
	private var dragSource: Option[ComposerDragSource] = None
	private var dropTarget: Option[ComposerDropTarget] = None

	val usedProvider = Var[Option[Rx[Set[Int]]]](None)

	val moveTracker: js.Function1[MouseEvent, Unit] = (ev: MouseEvent) => {
		val bounding = getBoundingClientRect()
		dragItem.style.left = ev.clientX - bounding.left + 10 + "px"
		dragItem.style.top = ev.clientY - bounding.top + 10 + "px"
	}

	listenCustom[(Int, MouseEvent, Option[ComposerDragSource])]("pickup-toon") { case (toon, ev, source) =>
		Rx.atomically {
			dragSource = source
			picked := toon
			hasPicked := true
			dropTarget = None
			moveTracker(ev)
			dom.document.addEventListener("mousemove", moveTracker)
		}
	}

	listenCustom("set-drop-target") { (t: ComposerDropTarget) =>
		if (hasPicked) dropTarget = Some(t)
	}

	listenCustom("unset-drop-target") { (t: ComposerDropTarget) =>
		if (dropTarget.contains(t)) dropTarget = None
	}

	listenCustom("used-toon-provider") { (prov: Rx[Set[Int]]) =>
		usedProvider := Some(prov)
	}

	listen("mouseup", dom.document) { (e: MouseEvent) =>
		Rx.atomically {
			for (source <- dragSource) source.clear(dropTarget)
			for (target <- dropTarget) target.accept(picked)
			hasPicked := false
			picked := 0
			dom.document.removeEventListener("mousemove", moveTracker)
		}
	}
}
