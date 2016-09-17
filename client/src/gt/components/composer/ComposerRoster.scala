package gt.components.composer

import _root_.data.Role.{DPS, Healing, Tank}
import data.UserGroups
import gt.Router
import gt.components.GtHandler
import gt.components.calendar.CalendarUnitFrame
import gt.components.widget.{GtAlert, GtContextMenu, GtTooltip}
import gt.services.{ComposerService, RosterService}
import models.Toon
import org.scalajs.dom
import org.scalajs.dom.MouseEvent
import rx.{Const, Rx}
import scala.annotation.switch
import utils.annotation.data
import utils.jsannotation.js
import xuen.Component

/**
  * The composer view.
  */
object ComposerRoster extends Component[ComposerRoster](
	selector = "composer-roster",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq(CalendarUnitFrame, GtContextMenu, GtTooltip, GtAlert)
) {
	@inline def ownerCategory(toon: Toon): String = (RosterService.user(toon.owner).group: @switch) match {
		case UserGroups.Officer | UserGroups.Member | UserGroups.Apply => "Mains"
		case UserGroups.Casual => "Casuals"
		case UserGroups.Veteran => "Veterans"
		case UserGroups.Guest => "Guests"
		case _ => "Unknown"
	}

	case class Filter(roster: Boolean, casuals: Boolean, veterans: Boolean, guests: Boolean) {
		def matches(toon: Toon) = ownerCategory(toon) match {
			case "Mains" => roster
			case "Casuals" => casuals
			case "Veterans" => veterans
			case "Guests" => guests
			case _ => false
		}
	}

	object Filter {
		def default = Filter(true, false, false, false)
	}

	val breakpoints = Seq(860, 840)
	val ordering = Seq("Mains", "Casuals", "Veterans", "Guests").zipWithIndex.toMap

	@data case class RosterGroup(title: String, toons: Iterable[Rx[Toon]]) {
		def sorted: Iterable[Rx[Toon]] = toons.toSeq.filter(_.active).sortWith { (x, y) =>
			val a = x.!
			val b = y.!
			if (a.role !=  b.role) {
				(a.role, b.role) match {
					case (Tank, _) => true
					case (Healing, DPS) => true
					case _ => false
				}
			} else if (a.ilvl != b.ilvl) {
				a.ilvl > b.ilvl
			} else {
				a.name < b.name
			}
		}
	}
}

@js class ComposerRoster extends GtHandler {
	private val composer = service(ComposerService)
	private val roster = service(RosterService)

	val filter = property[ComposerRoster.Filter] := ComposerRoster.Filter.default

	val picked = property[Int]
	val provider = property[Option[Rx[Set[Int]]]] := None

	def isUsed(toon: Int) = provider.getOrElse(Const(Set.empty[Int])).contains(toon)

	val pool = roster.toons.values ~ (ts => ts.filter(t => filter.matches(t.!)))

	val groups = pool ~ { p =>
		p.groupBy { t =>
			if (t.main) ComposerRoster.ownerCategory(t)
			else ComposerRoster.breakpoints.find(bp => t.ilvl > bp).map(bp => s"Alts $bp+").getOrElse("Crappies")
		}.map(ComposerRoster.RosterGroup.tupled).toSeq.sortWith { (a, b) =>
			if (a.title == "Crappies" || b.title == "Crappies") b.title == "Crappies"
			else {
				(ComposerRoster.ordering.get(a.title), ComposerRoster.ordering.get(b.title)) match {
					case (Some(x), Some(y)) => x < y
					case (Some(_), None) => true
					case (None, Some(_)) => false
					case _ => a.title > b.title
				}
			}
		}
	}

	val empty = groups ~ (_.isEmpty)

	def gotoProfile(profile: Int): Unit = Router.goto(s"/profile/$profile")
	def ownerName(id: Int): String = roster.user(id).name
	def openBnet(toon: Toon): Unit = dom.window.open(s"http://eu.battle.net/wow/en/character/${toon.server}/${toon.name}/advanced", "_blank")
	def pickup(id: Int, ev: MouseEvent): Unit = fire("pickup-toon", (id, ev, None))
}
