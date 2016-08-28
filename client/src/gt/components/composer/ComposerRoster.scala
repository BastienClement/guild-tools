package gt.components.composer

import data.UserGroups
import gt.components.GtHandler
import gt.services.{ComposerService, RosterService}
import models.Toon
import scala.annotation.switch
import utils.jsannotation.js
import xuen.Component

/**
  * The composer view.
  */
object ComposerRoster extends Component[ComposerRoster](
	selector = "composer-roster",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq()
) {
	case class Filter(roster: Boolean, casuals: Boolean, veterans: Boolean, guests: Boolean) {
		def matches(toon: Toon) = (RosterService.user(toon.owner).group: @switch) match {
			case UserGroups.Officer | UserGroups.Member | UserGroups.Apply => roster
			case UserGroups.Casual => casuals
			case UserGroups.Veteran => veterans
			case UserGroups.Guest => guests
			case _ => false
		}
	}

	object Filter {
		def default = Filter(true, false, false, false)
	}
}

@js class ComposerRoster extends GtHandler {
	private val composer = service(ComposerService)
	private val roster = service(RosterService)

	val filter = property[ComposerRoster.Filter] := ComposerRoster.Filter.default
}
