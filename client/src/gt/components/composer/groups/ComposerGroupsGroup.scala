package gt.components.composer.groups

import boopickle.DefaultBasic._
import data.{Armor, Relic, Role, Token}
import gt.components.GtHandler
import gt.components.calendar.CalendarUnitFrame
import gt.components.composer.{ComposerDragSource, ComposerDropTarget, ComposerRoster, ComposerToonTooltip}
import gt.components.widget.form.GtButton
import gt.components.widget.{GtBox, GtTooltip}
import gt.services.RosterService
import gtp3.Channel
import models.Toon
import models.composer.{ComposerGroup, ComposerGroupSlot}
import org.scalajs.dom.MouseEvent
import rx.{Rx, Var}
import scala.collection.mutable
import utils.jsannotation.js
import xuen.Component

object ComposerGroupsGroup extends Component[ComposerGroupsGroup](
	selector = "composer-groups-group",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(GtBox, GtButton, CalendarUnitFrame, ComposerToonTooltip, GtTooltip)
)

@js class ComposerGroupsGroup extends GtHandler {
	val roster = service(RosterService)
	val channel = property[Channel]
	val group = property[ComposerGroup]
	val slots = property[Set[ComposerGroupSlot]]

	def getToon(id: Int): Toon = roster.toon(id)

	val tiers = Rx {
		if (slots.isEmpty) -1
		else slots.map(_.tier).max
	}

	def slotsForTier(tier: Int) = slots.view.filter(_.tier == tier).toSeq.sortWith { (a, b) =>
		ComposerRoster.compareToons(roster.toon(a.toon), roster.toon(b.toon))
	}

	val hover = Var(-1)
	val focused = Var(Set.empty[Int])

	def isFocused(toon: Int) = focused.contains(toon)

	def isConflict(toon: Int) = {
		val owner = roster.toon(toon).owner
		ownersCount.getOrElse(owner, 0) > 1
	}

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
		if (force) {
			hover := -1
			fire("unset-drop-target", targetForTier(tier))
		}
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
		if (ev.shiftKey) {
			if (focused.contains(toon)) focused ~= (_ - toon)
			else focused ~= (_ + toon)
		} else if (ev.button == 0) {
			fire("pickup-toon", (toon, ev, Some(new ComposerDragSource {
				def clear(target: Option[ComposerDropTarget]): Unit = target match {
					case Some(trg) if targetCache.exists { case (_, t) => t == trg } => // ignore (move)
					case _ => channel.send("unset-slot", (group.id, toon))
				}
			})))
			enterTier(tier)
		}
	}

	//
	// Stats
	//
	val toons = slots ~ (_.map(slot => roster.toon(slot.toon).!).toList)

	val ownersCount = toons ~ (_.groupBy(_.owner).mapValues(_.size))

	val focusedToons = toons ~ { t =>
		if (focused.nonEmpty) t.filter(toon => focused.contains(toon.id))
		else t
	}

	def count = focusedToons.size
	def overallIlvl = focusedToons.map(_.ilvl).sum / count

	val tanks = focusedToons ~ (_.filter(_.spec.role == Role.Tank))
	val healers = focusedToons ~ (_.filter(_.spec.role == Role.Healing))
	val dps = focusedToons ~ (_.filter(_.spec.role == Role.DPS))

	def tanksCount = tanks.size
	def healersCount = healers.size
	def dpsCount = dps.size

	def tanksIlvl = tanks.map(_.ilvl).sum / tanksCount
	def healersIlvl = healers.map(_.ilvl).sum / healersCount
	def dpsIlvl = dps.map(_.ilvl).sum / dpsCount

	val cloths = focusedToons ~ (_.filter(_.clss.armor == Armor.Cloth))
	val leathers = focusedToons ~ (_.filter(_.clss.armor == Armor.Leather))
	val mails = focusedToons ~ (_.filter(_.clss.armor == Armor.Mail))
	val plates = focusedToons ~ (_.filter(_.clss.armor == Armor.Plate))

	def clothsCount = cloths.size
	def leathersCount = leathers.size
	def mailsCount = mails.size
	def platesCount = plates.size

	val conquerors = focusedToons ~ (_.filter(_.clss.token == Token.Conqueror))
	val protectors = focusedToons ~ (_.filter(_.clss.token == Token.Protector))
	val vanquishers = focusedToons ~ (_.filter(_.clss.token == Token.Vanquisher))

	def conquerorsCount = conquerors.size
	def protectorsCount = protectors.size
	def vanquishersCount = vanquishers.size

	def hasRelic(relic: Relic): Seq[Toon] => Seq[Toon] = (focusedToons: Seq[Toon]) => {
		focusedToons.filter(_.spec.artifact.relics.productIterator.contains(relic))
	}

	val arcaneRelics = focusedToons ~ hasRelic(Relic.Arcane)
	val bloodRelics = focusedToons ~ hasRelic(Relic.Blood)
	val felRelics = focusedToons ~ hasRelic(Relic.Fel)
	val fireRelics = focusedToons ~ hasRelic(Relic.Fire)
	val frostRelics = focusedToons ~ hasRelic(Relic.Frost)
	val holyRelics = focusedToons ~ hasRelic(Relic.Holy)
	val ironRelics = focusedToons ~ hasRelic(Relic.Iron)
	val lifeRelics = focusedToons ~ hasRelic(Relic.Life)
	val shadowRelics = focusedToons ~ hasRelic(Relic.Shadow)
	val stormRelics = focusedToons ~ hasRelic(Relic.Storm)

	def arcaneRelicsCount = arcaneRelics.size
	def bloodRelicsCount = bloodRelics.size
	def felRelicsCount = felRelics.size
	def fireRelicsCount = fireRelics.size
	def frostRelicsCount = frostRelics.size
	def holyRelicsCount = holyRelics.size
	def ironRelicsCount = ironRelics.size
	def lifeRelicsCount = lifeRelics.size
	def shadowRelicsCount = shadowRelics.size
	def stormRelicsCount = stormRelics.size
}

