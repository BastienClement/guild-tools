package utils

import models.ChatMessages
import models.mysql._

object ChatSelect extends ChatSelect(None, None, None, None, None) {
	val all = this
}

case class ChatSelect(
		fromId: Option[Int],
		toId: Option[Int],
		fromTime: Option[SmartTimestamp],
		toTime: Option[SmartTimestamp],
		limitCount: Option[Int]) {
	/** Define the lower limit for both the ID and the timestamp */
	def from(id: Int) = this.copy(fromId = id)
	def from(ts: SmartTimestamp) = this.copy(fromTime = ts)

	/** Define the upper limit for both the ID and the timestamp */
	def to(id: Int) = this.copy(fromId = id)
	def to(ts: SmartTimestamp) = this.copy(fromTime = ts)

	/** Define the message limit */
	def limit(count: Int) = this.copy(limitCount = count)

	/** Apply this filter to request */
	def toQuery = {
		var query = ChatMessages.take(limitCount.getOrElse(100))

		for (id <- fromId) query = query.filter(_.id >= id)
		for (id <- toId) query = query.filter(_.id <= id)

		for (time <- fromTime) query = query.filter(_.date <= time.toSQL)
		for (time <- toTime) query = query.filter(_.date >= time.toSQL)

		query
	}
}
