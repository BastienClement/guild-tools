package model.calendar

import utils.annotation.data

@data case class Tab(id: Int, event: Int, title: String, note: Option[String],
                     order: Int, locked: Boolean, undeletable: Boolean)
