package models.composer

import boopickle.DefaultBasic._
import utils.annotation.data

@data case class ComposerGroup(id: Int, doc: Int, title: String)

object ComposerGroup {
	implicit val ComposerGroupPickler = PicklerGenerator.generatePickler[ComposerGroup]
}

