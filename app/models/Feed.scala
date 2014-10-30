package models

import java.sql.Timestamp
import models.mysql._

case class Feed(guid: String, source: String, title: String, link: String, time: Timestamp, tags: String)

/**
 * Answers database
 */
class Feeds(tag: Tag) extends Table[Feed](tag, "gt_feed") {
	def guid = column[String]("guid", O.PrimaryKey)
	def source = column[String]("source")
	def title = column[String]("title")
	def link = column[String]("link")
	def time = column[Timestamp]("time")
	def tags = column[String]("tags")

	def * = (guid, source, title, link, time, tags) <> (Feed.tupled, Feed.unapply)
}

/**
 * Helpers
 */
object Feeds extends TableQuery(new Feeds(_)) {
	def notifyCreate(entry: Feed): Unit = {
	}

	def notifyUpdate(entry: Feed): Unit = {
	}

	def notifyDelete(id: Int): Unit = {
	}
}
