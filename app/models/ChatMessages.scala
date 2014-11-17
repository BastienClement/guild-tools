package models

import models.mysql._
import java.sql.Timestamp

case class ChatMessage(id: Int, channel: Option[Int], user: Option[Int], from: String, date: Timestamp, text: String)

class ChatMessages(tag: Tag) extends Table[ChatMessage](tag, "gt_chat_messages") {
	def id = column[Int]("id", O.PrimaryKey)
	def channel = column[Option[Int]]("channel")
	def user = column[Option[Int]]("from_id")
	def from = column[String]("from")
	def date = column[Timestamp]("date")
	def text = column[String]("text")

	def * = (id, channel, user, from, date, text) <> (ChatMessage.tupled, ChatMessage.unapply)
}

object ChatMessages extends TableQuery(new ChatMessages(_))
