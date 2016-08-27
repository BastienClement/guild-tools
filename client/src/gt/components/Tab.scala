package gt.components

import utils.annotation.data

@data case class Tab(title: String, link: String, active: Boolean = false, hidden: Boolean = false)
