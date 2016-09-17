package models.composer

import boopickle.DefaultBasic._
import utils.annotation.data

@data case class ComposerGroupSlot(group: Int, toon: Int, tier: Int, spec: Option[Int])

object ComposerGroupSlot {
	implicit val ComposerGroupSlotPickler = PicklerGenerator.generatePickler[ComposerGroupSlot]
}
