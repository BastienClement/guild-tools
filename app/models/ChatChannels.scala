package models

import models.mysql._

case class ChatChannel(id: Int, title: String)

class ChatChannels(tag: Tag) extends Table[ChatChannel](tag, "gt_bugsack") {
	def id = column[Int]("id", O.PrimaryKey)
	def title = column[String]("title")

	def * = (id, title) <> (ChatChannel.tupled, ChatChannel.unapply)
}

object ChatChannels extends TableQuery(new ChatChannels(_))
