package util

import java.sql.Timestamp
import slick.driver.MySQLDriver.api._

trait DateTimeImplicits {
	// Slick definitions
	implicit val DateTimeColumnType = MappedColumnType.base[DateTime, Timestamp](_.toTimestamp, DateTime.fromTimestamp)
}
