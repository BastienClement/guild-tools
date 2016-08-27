package models

import models.mysql._
import utils.DateTime

case class ChatMessage(id: Int, room: Int, user: Option[Int], from: String, text: String, date: DateTime = DateTime.now)

class ChatMessages(tag: Tag) extends Table[ChatMessage](tag, "gt_chat_messages") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def room = column[Int]("room")
	def user = column[Option[Int]]("from_id")
	def from = column[String]("from")
	def text = column[String]("text")
	def date = column[DateTime]("date")

	def * = (id, room, user, from, text, date) <> (ChatMessage.tupled, ChatMessage.unapply)
}

object ChatMessages extends TableQuery(new ChatMessages(_))
