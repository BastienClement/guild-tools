package models

import models.mysql._

case class ChatRoom(id: Int, title: String)

class ChatRooms(tag: Tag) extends Table[ChatRoom](tag, "gt_chat_rooms") {
	def id = column[Int]("id", O.PrimaryKey)
	def title = column[String]("title")

	def * = (id, title) <> (ChatRoom.tupled, ChatRoom.unapply)
}

object ChatRooms extends TableQuery(new ChatRooms(_))
