package models.calendar

import boopickle.DefaultBasic._
import utils.annotation.data

@data case class Tab(id: Int, event: Int, title: String, note: Option[String],
                     order: Int, locked: Boolean, undeletable: Boolean)

object Tab {
	implicit val TabPickler = PicklerGenerator.generatePickler[Tab]
}
