package util

import java.sql.Timestamp
import java.time._
import slick.driver.MySQLDriver.api._

trait DateTimeCompatImpl {
	val slickMapping = MappedColumnType.base[DateTime, Timestamp](_.toTimestamp, DateTime.fromTimestamp)
	implicit val compat = DateTimeCompatImpl
}

object DateTimeCompatImpl extends DateTimeCompat {
	def instantAtOffset(instant: Instant, offset: ZoneOffset): OffsetDateTime = instant.atOffset(offset)
}
