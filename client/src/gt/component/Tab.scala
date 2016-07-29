package gt.component

import util.annotation.data

@data case class Tab(title: String, link: String, active: Boolean = false, hidden: Boolean = false)
