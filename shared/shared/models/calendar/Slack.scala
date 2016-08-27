package models.calendar

import utils.DateTime
import utils.annotation.data

/**
  * Created by galedric on 11.08.2016.
  */
@data case class Slack(id: Int, user: Int, from: DateTime, to: DateTime, reason: Option[String]) {
	lazy val conceal = this.copy(reason = None)
}
