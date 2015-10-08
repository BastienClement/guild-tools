import java.sql.Timestamp
import java.text.SimpleDateFormat

import play.api.Play
import play.api.db.slick.DatabaseConfigProvider
import play.api.libs.json._
import slick.dbio.{NoStream, DBIOAction}
import slick.driver.JdbcProfile
import slick.lifted.Query

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.implicitConversions

package object models {
	val DB = DatabaseConfigProvider.get[JdbcProfile](Play.current).db
	val simple = slick.driver.MySQLDriver.simple
	val mysql = slick.driver.MySQLDriver.api
	val sql = slick.jdbc.StaticQuery

	implicit class QueryExecutor[A](val q: Query[_, A, Seq]) extends AnyVal {
		import mysql._
		def run = DB.run(q.result)
		def head = DB.run(q.result.head)
		def headOption = DB.run(q.result.headOption)
	}

	implicit class DBActionExecutior[R](val q: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		def run = DB.run(q)
		def await = DB.run(q).await
	}

	implicit class AwaitableFuture[A](val f: Future[A]) extends AnyVal {
		def await: A = await(30.seconds)
		def await(limit: Duration): A = Await.result(f, limit)
	}

	implicit val timestampFormat = new Format[Timestamp] {
		val format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
		def reads(json: JsValue) = JsSuccess(new Timestamp(format.parse(json.as[String]).getTime))
		def writes(ts: Timestamp) = JsString(format.format(ts))
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
