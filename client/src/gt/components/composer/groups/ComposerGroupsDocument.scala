package gt.components.composer.groups

import boopickle.DefaultBasic._
import gt.Server
import gt.components.GtHandler
import gt.components.composer.ComposerDropTarget
import gt.components.widget.form.{GtButton, GtForm, GtInput}
import gt.components.widget.{GtBox, GtDialog}
import gt.services.ComposerService
import gt.services.base.{Cache, Delegate}
import gtp3.Channel
import models.composer.{ComposerDocument, ComposerGroup, ComposerGroupSlot}
import rx.Var
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import utils.jsannotation.js
import xuen.Component

object ComposerGroupsDocument extends Component[ComposerGroupsDocument](
	selector = "composer-groups-document",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox, GtDialog, GtForm, GtInput, GtButton, ComposerGroupsGroup)
)

@js class ComposerGroupsDocument extends GtHandler {
	val composer = service(ComposerService)
	val doc = property[ComposerDocument]

	var dragging = property[Boolean]
	var loading = Var(true)

	def renameDocument(title: String): Unit = composer.renameDocument(doc.id, title)
	def deleteDocument(): Unit = composer.deleteDocument(doc.id)

	object Groups extends Cache((grp: ComposerGroup) => grp.id)
	object Slots extends Cache((slot: ComposerGroupSlot) => (slot.group, slot.toon)) {
		val byGroup = new SimpleIndex(slot => slot.group)
	}

	def slotsForGroup(group: Int): Set[ComposerGroupSlot] = Slots.byGroup.get(group)

	val channel = Var[Channel](null)

	object ChannelDelegate extends Delegate {
		message("group-updated")(Groups.update _)
		message("group-deleted") { (id: Int) =>
			Groups.removeKey(id)
			Slots.prune(_.group == id)
		}
		message("slot-updated")(Slots.update _)
		message("slot-deleted")(Slots.removeKey _)
	}

	def nextGroupCount = Groups.values.size + 1
	def createGroup(): Unit = channel.send("create-group", (doc.id, "Group " + nextGroupCount))

	val groups = Groups.values ~ (_.map(_.!).toSeq.sortBy(_.id))

	override def attached(): Unit = {
		if (channel.! != null) {
			channel.close()
			channel := null
		}

		fire("used-toon-provider", Slots.values ~ (slots => slots.map(_.toon).toSet))

		Server.openChannel("composer-document", doc.id.toString).onComplete {
			case Failure(e) => println(e.getMessage)
			case Success(chan) =>
				channel := chan
				channel.onMessage ~> (ChannelDelegate.receiveMessage _).tupled
				for (groups <- channel.request("load-groups", ()).as[Seq[ComposerGroup]]) {
					groups.foreach(Groups.update)
					for (slots <- channel.request("load-slots", ()).as[Seq[ComposerGroupSlot]]) {
						slots.foreach(Slots.update)
					}
					loading := false
				}
		}
	}

	override def detached(): Unit = {
		if (channel.! != null) {
			channel.close()
			channel := null
		}
	}

	def mkGroupTarget(id: Int): ComposerDropTarget = new ComposerDropTarget {
		def accept(toon: Int): Unit = println("Accept", id)
	}

	def enterTarget(target: ComposerDropTarget): Unit = fire("set-drop-target", target)
	def leaveTarget(target: ComposerDropTarget): Unit = fire("unset-drop-target", target)
}

