package models

import java.sql.Timestamp
import models.mysql._

case class BugReport(key: String, user: Int, date: Timestamp, rev: String, error: String, stack: String)

/**
 * Bug reports database
 */
class BugSack(tag: Tag) extends Table[BugReport](tag, "gt_bugsack") {
	def key = column[String]("key", O.PrimaryKey)
	def user = column[Int]("user")
	def date = column[Timestamp]("date")
	def rev = column[String]("rev")
	def error = column[String]("error")
	def stack = column[String]("stack")

	def * = (key, user, date, rev, error, stack) <> (BugReport.tupled, BugReport.unapply)
}

object BugSack extends TableQuery(new BugSack(_))
