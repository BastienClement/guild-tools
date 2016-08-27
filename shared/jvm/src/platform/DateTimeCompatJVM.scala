package platform

import java.time.{Instant, OffsetDateTime, ZoneOffset}

object DateTimeCompatJVM extends DateTimeCompat {
	def instantAtOffset(instant: Instant, offset: ZoneOffset): OffsetDateTime = instant.atOffset(offset)
}
