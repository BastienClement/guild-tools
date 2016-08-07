package models

import models.mysql._
import util.DateTime

case class Feed(guid: String, source: String, title: String, link: String, time: DateTime, tags: String)

class Feeds(tag: Tag) extends Table[Feed](tag, "gt_feed") {
	def guid = column[String]("guid", O.PrimaryKey)
	def source = column[String]("source")
	def title = column[String]("title")
	def link = column[String]("link")
	def time = column[DateTime]("time")
	def tags = column[String]("tags")

	def * = (guid, source, title, link, time, tags) <> (Feed.tupled, Feed.unapply)
}

object Feeds extends TableQuery(new Feeds(_))
