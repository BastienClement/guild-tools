package gt.components.composer

import data.Relic
import gt.components.GtHandler
import gt.services.{ComposerService, RosterService}
import models.Toon
import utils.jsannotation.js
import xuen.Component

object ComposerToonTooltip extends Component[ComposerToonTooltip](
	selector = "composer-toon-tooltip",
	templateUrl = "/assets/imports/views/composer.html",
	dependencies = Seq()
)

@js class ComposerToonTooltip extends GtHandler {
	private val composer = service(ComposerService)
	private val roster = service(RosterService)

	val toon = property[Toon]

	def ownerName(id: Int): String = roster.user(id).name
	def spec = toon.spec.name
	def artifact = toon.spec.artifact.name
	def relics = toon.spec.artifact.relics.productIterator.map(_.asInstanceOf[Relic].name).mkString(" / ")
}
