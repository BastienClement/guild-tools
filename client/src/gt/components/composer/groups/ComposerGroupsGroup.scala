package gt.components.composer.groups

import boopickle.DefaultBasic._
import gt.components.GtHandler
import gt.components.calendar.CalendarUnitFrame
import gt.components.composer.{ComposerDragSource, ComposerDropTarget}
import gt.components.widget.GtBox
import gt.components.widget.form.GtButton
import gtp3.Channel
import models.composer.{ComposerGroup, ComposerGroupSlot}
import org.scalajs.dom.MouseEvent
import rx.{Rx, Var}
import scala.collection.mutable
import utils.jsannotation.js
import xuen.Component

object ComposerGroupsGroup extends Component[ComposerGroupsGroup](
	selector = "composer-groups-group",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox, GtButton, CalendarUnitFrame)
)

@js class ComposerGroupsGroup extends GtHandler {
	val channel = property[Channel]
	val group = property[ComposerGroup]
	val slots = property[Set[ComposerGroupSlot]]

	val tiers = Rx {
		if (slots.isEmpty) -1
		else slots.map(_.tier).max
	}

	def slotsForTier(tier: Int) = slots.view.filter(_.tier == tier).toSeq

	val hover = Var(-1)

	def deleteGroup(): Unit = channel.send("delete-group", group.id)

	private val targetCache = mutable.Map[Int, ComposerDropTarget]()

	private def targetForTier(tier: Int) = {
		targetCache.getOrElseUpdate(tier, {
			new ComposerDropTarget {
				def accept(toon: Int): Unit = channel.send("set-slot", ComposerGroupSlot(group.id, toon, tier, None))
			}
		})
	}

	def enterTier(tier: Int): Unit = {
		hover := tier
		fire("set-drop-target", targetForTier(tier))
	}

	def leaveTier(tier: Int, force: Boolean = false): Unit = if (tier > 0 || force) {
		hover := -1
		fire("unset-drop-target", targetForTier(tier))
	}

	listen("mouseenter") { (ev: MouseEvent) =>
		setAttribute("hover", "")
		if (hover < 0) {
			enterTier(0)
		}
	}

	listen("mouseleave") { (ev: MouseEvent) =>
		removeAttribute("hover")
		leaveTier(0, true)
	}

	def beginDrag(tier: Int, toon: Int, ev: MouseEvent): Unit = {
		fire("pickup-toon", (toon, ev, Some(new ComposerDragSource {
			def clear(target: Option[ComposerDropTarget]): Unit = target match {
				case Some(trg) if targetCache.exists { case (_, t) => t == trg } => // ignore (move)
				case _ => channel.send("unset-slot", (group.id, toon))
			}
		})))
	}
}

