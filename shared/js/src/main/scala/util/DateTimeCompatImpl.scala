package util

import java.time._
import scala.scalajs.js

trait DateTimeCompatImpl {
	trait IgnoredImplicit
	val slickMapping: IgnoredImplicit = null

	implicit val compat = DateTimeCompatImpl
}

object DateTimeCompatImpl extends DateTimeCompat {
	def instantAtOffset(instant: Instant, offset: ZoneOffset): OffsetDateTime = {
		require(offset == null, "Only UTC offset is supported")
		OffsetDateTime.of(toLocalDateTime(new js.Date(instant.toEpochMilli)), null)
	}

	def toLocalDate(d: js.Date): LocalDate = {
		LocalDate.of(d.getUTCFullYear, d.getUTCMonth + 1, d.getUTCDate)
	}

	def toLocalTime(d: js.Date): LocalTime = {
		LocalTime.of(d.getHours, d.getUTCMinutes, d.getUTCMinutes, d.getUTCMilliseconds * 1000000)
	}

	def toLocalDateTime(d: js.Date): LocalDateTime = {
		LocalDateTime.of(toLocalDate(d), toLocalTime(d))
	}

	def toOffsetDateTime(d: js.Date): OffsetDateTime = {
		OffsetDateTime.of(toLocalDateTime(d), null)
	}
}
