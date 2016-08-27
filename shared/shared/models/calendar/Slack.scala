package models.calendar

import boopickle.DefaultBasic._
import utils.DateTime
import utils.annotation.data

@data case class Slack(id: Int, user: Int, from: DateTime, to: DateTime, reason: Option[String]) {
	lazy val conceal = this.copy(reason = None)
}

object Slack {
	implicit val SlackPickler = PicklerGenerator.generatePickler[Slack]
}
