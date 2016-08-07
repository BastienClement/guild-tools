package java.time

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAmount
import scala.language.implicitConversions
import scala.scalajs.js
import util.DateTimeCompatImpl

final class OffsetDateTime(val dateTime: LocalDateTime, offset: ZoneOffset) {
	@inline implicit private def localAsOffset(local: LocalDateTime): OffsetDateTime = {
		new OffsetDateTime(local, null)
	}

	def toInstant: Instant = dateTime.toInstant(offset)

	def format(formatter: DateTimeFormatter): String = {
		require(formatter == null, "Only UTF formatter is supported")
		new js.Date(toInstant.toEpochMilli).toISOString()
	}

	def getYear: Int = dateTime.getYear
	def getMonthValue: Int = dateTime.getMonthValue
	def getDayOfMonth: Int = dateTime.getDayOfMonth
	def getHour: Int = dateTime.getHour
	def getMinute: Int = dateTime.getMinute
	def getSecond: Int = dateTime.getSecond
	def getNano: Int = dateTime.getNano

	def plus(duration: TemporalAmount): OffsetDateTime = dateTime.plus(duration)
	def minus(duration: TemporalAmount): OffsetDateTime = dateTime.minus(duration)
}

object OffsetDateTime {

	def parse(text: CharSequence, formatter: DateTimeFormatter): OffsetDateTime = {
		require(formatter == null, "Only UTF formatter is supported")
		DateTimeCompatImpl.toOffsetDateTime(new js.Date(text.toString))
	}

	def of(dateTime: LocalDateTime, offset: ZoneOffset): OffsetDateTime = new OffsetDateTime(dateTime, offset)
}
