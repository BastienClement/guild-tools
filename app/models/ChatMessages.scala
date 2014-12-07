package models

import models.mysql._
import java.sql.Timestamp
import utils.SmartTimestamp

case class ChatMessage(id: Int, channel: Option[Int], user: Option[Int], from: String, text: String, date: Timestamp = SmartTimestamp.now)

class ChatMessages(tag: Tag) extends Table[ChatMessage](tag, "gt_chat_messages") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def channel = column[Option[Int]]("channel")
	def user = column[Option[Int]]("from_id")
	def from = column[String]("from")
	def text = column[String]("text")
	def date = column[Timestamp]("date")

	def * = (id, channel, user, from, text, date) <> (ChatMessage.tupled, ChatMessage.unapply)
}

object ChatMessages extends TableQuery(new ChatMessages(_))
