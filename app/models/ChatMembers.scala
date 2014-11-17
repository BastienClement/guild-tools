package models

import models.mysql._

case class ChatMembership(channel: Int, user: Int)

class ChatMembers(tag: Tag) extends Table[ChatMembership](tag, "gt_chat_members") {
	def channel = column[Int]("channel", O.PrimaryKey)
	def user = column[Int]("user", O.PrimaryKey)

	def * = (channel, user) <> (ChatMembership.tupled, ChatMembership.unapply)
}

object ChatMembers extends TableQuery(new ChatMembers(_))


