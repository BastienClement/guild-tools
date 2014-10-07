import java.sql.Timestamp
import java.util.Date

import play.api.Play.current
import play.api.libs.json._

package object models {
	val DB = play.api.db.slick.DB
	val mysql = scala.slick.driver.MySQLDriver.simple
	val sql = scala.slick.jdbc.StaticQuery

	def NOW() = new Timestamp(new Date().getTime)

	implicit val userJsonFormat = Json.format[User]
	implicit val charJsonFormat = Json.format[Char]
}
