package gt.components.profile

import data.Specializations
import gt.components.GtHandler
import gt.components.widget.form.GtButton
import gt.components.widget.{BnetThumb, GtBox}
import gt.services.RosterService
import models.Toon
import rx.syntax.MonadicOps
import rx.{Rx, Var}
import scala.concurrent.ExecutionContext.Implicits.global
import utils.jsannotation.js
import xuen.Component

object ProfileCharsCard extends Component[ProfileCharsCard](
	selector = "profile-chars-card",
	templateUrl = "/assets/imports/views/profile.html",
	dependencies = Seq(GtBox, GtButton, BnetThumb)
)

@js class ProfileCharsCard extends GtHandler {
	val char = property[Toon]
	val roster = service(RosterService)

	val tid = char ~ (_.id)
	val updatePending = Var(false)

	val specs = char ~ (c => Specializations.forClass(c.clss))

	val updatable = for {
		pending <- updatePending
		char <- char
		now <- Rx.time
	} yield {
		!pending && (now - char.last_update > 1000 * 60 * 15)
	}

	def setSpec(spec: Int): Unit = if (spec != char.specid) roster.changeToonSpec(tid, spec)
	def promote(): Unit = roster.promoteToon(tid)
	def enable(): Unit = roster.enableToon(tid)
	def disable(): Unit = roster.disableToon(tid)
	def delete(): Unit = roster.removeToon(tid)

	def update(): Unit = if (!updatePending) {
		updatePending := true
		roster.updateToon(tid).onComplete(_ => updatePending := false)
	}
}

