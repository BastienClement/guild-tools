package models.live

import utils.SlickAPI._

/**
  * A private stream for guildmates.
  */
case class Stream(token: String, user: Int, progress: Boolean)

class Streams(tag: Tag) extends Table[Stream](tag, "gt_streams") {
	def token = column[String]("token", O.PrimaryKey)
	def user = column[Int]("user")
	def secret = column[String]("secret")
	def progress = column[Boolean]("progress")

	def * = (token, user, progress) <> (Stream.tupled, Stream.unapply)
}

object Streams extends TableQuery(new Streams(_))
