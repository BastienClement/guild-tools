import java.sql.Timestamp
import java.text.SimpleDateFormat
import scala.concurrent.Future
import scala.concurrent.{Awaitable, Await}
import scala.language.implicitConversions
import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import slick.driver.JdbcProfile
import slick.lifted.{QueryBase, Query}
import scala.concurrent.duration._

package object models {
	val DB = DatabaseConfigProvider.get[JdbcProfile](Play.current).db
	val simple = slick.driver.MySQLDriver.simple
	val mysql = slick.driver.MySQLDriver.api
	val sql = slick.jdbc.StaticQuery

	implicit object timestampFormat extends Format[Timestamp] {
		val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
		def reads(json: JsValue) = JsSuccess(new Timestamp(format.parse(json.as[String]).getTime))
		def writes(ts: Timestamp) = JsString(format.format(ts))
	}

	implicit class QueryExecutor[A](query: Query[_, A, Seq]) {
		import mysql._
		def run = DB.run(query.result)
		def head = DB.run(query.result.head)
		def headOption = DB.run(query.result.headOption)
	}



	implicit val userJsonWriter = new Writes[User] {
		def writes(user: User): JsValue = {
			Json.obj(
				"id" -> user.id,
				"name" -> user.name,
				"group" -> user.group,
				"color" -> user.color,
				"officer" -> user.officer,
				"promoted" -> user.promoted,
				"developer" -> user.developer)
		}
	}

	implicit val charJsonFormat = Json.format[Char]
	implicit val eventJsonFormat = Json.format[CalendarEvent]
	implicit val answerJsonFormat = Json.format[CalendarAnswer]
	implicit val tabJsonFormat = Json.format[CalendarTab]
	implicit val slotJsonFormat = Json.format[CalendarSlot]
	implicit val eventFullJsonFormat = Json.format[CalendarEventFull]
	implicit val feedJsonFormat = Json.format[Feed]
	implicit val absenceJsonFormat = Json.format[Slack]
	implicit val chatMessageFormat = Json.format[ChatMessage]
	implicit val chatWhisperFormat = Json.format[ChatWhisper]
	implicit val composerLockoutFormat = Json.format[ComposerLockout]
	implicit val composerGroupFormat = Json.format[ComposerGroup]
	implicit val composerSlotFormat = Json.format[ComposerSlot]
}
