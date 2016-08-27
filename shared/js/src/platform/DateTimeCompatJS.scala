package platform

import java.time._
import scala.scalajs.js

object DateTimeCompatJS extends DateTimeCompat{
	def instantAtOffset(instant: Instant, offset: ZoneOffset): OffsetDateTime = {
		require(offset == null, "Only UTC offset is supported")
		OffsetDateTime.of(toLocalDateTime(new scalajs.js.Date(instant.toEpochMilli)), null)
	}

	def toLocalDate(d: scalajs.js.Date): LocalDate = {
		LocalDate.of(d.getUTCFullYear, d.getUTCMonth + 1, d.getUTCDate)
	}

	def toLocalTime(d: scalajs.js.Date): LocalTime = {
		LocalTime.of(d.getHours, d.getUTCMinutes, d.getUTCMinutes, d.getUTCMilliseconds * 1000000)
	}

	def toLocalDateTime(d: js.Date): LocalDateTime = {
		LocalDateTime.of(toLocalDate(d), toLocalTime(d))
	}

	def toOffsetDateTime(d: js.Date): OffsetDateTime = {
		OffsetDateTime.of(toLocalDateTime(d), null)
	}
}
