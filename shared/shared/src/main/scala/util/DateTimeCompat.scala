package util

import java.time.{Instant, OffsetDateTime, ZoneOffset}

trait DateTimeCompat {
	def instantAtOffset(instant: Instant, offset: ZoneOffset): OffsetDateTime
}

object DateTimeCompat extends DateTimeCompatImpl
