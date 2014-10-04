import play.api.Play.current
import scala.slick.lifted.SimpleFunction
import java.sql.Timestamp

package object models {
	val DB = play.api.db.slick.DB
	val MySQL = scala.slick.driver.MySQLDriver.simple
	val sql = scala.slick.jdbc.StaticQuery
}
