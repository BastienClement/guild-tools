package models

import models.mysql._
import util.DateTime

case class ChatWhisper(id: Int, from: Int, to: Int, date: DateTime, text: String)

class ChatWhispers(tag: Tag) extends Table[ChatWhisper](tag, "gt_chat_whispers") {
	def id = column[Int]("id", O.PrimaryKey)
	def from = column[Int]("from")
	def to = column[Int]("to")
	def date = column[DateTime]("date")
	def text = column[String]("text")

	def * = (id, from, to, date, text) <> (ChatWhisper.tupled, ChatWhisper.unapply)
}

object ChatWhispers extends TableQuery(new ChatWhispers(_))


