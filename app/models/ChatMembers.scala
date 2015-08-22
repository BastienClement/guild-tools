package models

import models.mysql._

case class ChatMembership(channel: Int, user: Int, unread: Boolean)

class ChatMembers(tag: Tag) extends Table[ChatMembership](tag, "gt_chat_members") {
	def channel = column[Int]("channel", O.PrimaryKey)
	def user = column[Int]("user", O.PrimaryKey)
	def unread = column[Boolean]("unread")

	def * = (channel, user, unread) <> (ChatMembership.tupled, ChatMembership.unapply)
}

object ChatMembers extends TableQuery(new ChatMembers(_))


