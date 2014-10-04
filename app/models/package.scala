import play.api.Play.current
import play.api.libs.json._

import java.util.Date
import java.sql.Timestamp

package object models {
	val DB = play.api.db.slick.DB
	val mysql = scala.slick.driver.MySQLDriver.simple
	val sql = scala.slick.jdbc.StaticQuery
	
	def NOW() = new Timestamp(new Date().getTime())

	implicit val userJsonFormat = Json.format[User]
	implicit val charJsonFormat = Json.format[Char]
}
