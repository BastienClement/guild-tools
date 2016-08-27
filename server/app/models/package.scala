import boopickle.DefaultBasic._
import com.google.inject.Inject
import models.application.{Application, ApplicationMessage}
import models.calendar._
import play.api.db.slick.DatabaseConfigProvider
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.implicitConversions
import slick.dbio.{DBIOAction, NoStream}
import slick.driver.JdbcProfile
import slick.lifted.Query

package object models {
	val pkg = this
	@Inject var dbc: DatabaseConfigProvider = null

	lazy val mysql = slick.driver.MySQLDriver.api
	lazy val DB = dbc.get[JdbcProfile].db

	implicit class QueryExecutor[A](val q: Query[_, A, Seq]) extends AnyVal {
		import mysql._
		@inline def run = DB.run(q.result)
		@inline def head = DB.run(q.result.head)
		@inline def headOption = DB.run(q.result.headOption)
	}

	implicit class DBIOActionExecutor[R](val q: DBIOAction[R, NoStream, Nothing]) extends AnyVal {
		@inline def run = DB.run(q)
		@inline def await = DB.run(q).await
	}

	implicit class AwaitableFuture[A](val f: Future[A]) extends AnyVal {
		@inline def await: A = await(30.seconds)
		@inline def await(limit: Duration): A = Await.result(f, limit)
	}

	implicit val applyJsonFormat = PicklerGenerator.generatePickler[Application]
	implicit val applyFeedMessageJsonFormat = PicklerGenerator.generatePickler[ApplicationMessage]
	implicit val eventJsonFormat = PicklerGenerator.generatePickler[Event]
	implicit val answerJsonFormat = PicklerGenerator.generatePickler[Answer]
	implicit val tabJsonFormat = PicklerGenerator.generatePickler[Tab]
	implicit val slotJsonFormat = PicklerGenerator.generatePickler[Slot]
	implicit val feedJsonFormat = PicklerGenerator.generatePickler[NewsFeedData]
	implicit val absenceJsonFormat = PicklerGenerator.generatePickler[Slack]
	implicit val chatMessageFormat = PicklerGenerator.generatePickler[ChatMessage]
	implicit val chatWhisperFormat = PicklerGenerator.generatePickler[ChatWhisper]
	implicit val composerLockoutFormat = PicklerGenerator.generatePickler[ComposerLockout]
	implicit val composerGroupFormat = PicklerGenerator.generatePickler[ComposerGroup]
	implicit val composerSlotFormat = PicklerGenerator.generatePickler[ComposerSlot]
	implicit val streamJsonFormat = PicklerGenerator.generatePickler[live.Stream]
	implicit val profileJsonFormat = PicklerGenerator.generatePickler[Profile]
}
