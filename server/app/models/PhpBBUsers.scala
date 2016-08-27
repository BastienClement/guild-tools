package models

import models.User
import models.mysql._

class PhpBBUsers(tag: Tag) extends Table[User](tag, "phpbb_users") {
	def id = column[Int]("user_id", O.PrimaryKey)
	def name = column[String]("username")
	def group = column[Int]("group_id")

	def pass = column[String]("user_password")
	def name_clean = column[String]("username_clean")
	def user_email = column[String]("user_email")

	def * = (id, name, group) <> (User.tupled, User.unapply)
}

object PhpBBUsers extends TableQuery(new PhpBBUsers(_)) {
	def findByUsername(username: Rep[String]) = {
		val user = username.toLowerCase
		PhpBBUsers.filter(u => u.name.toLowerCase === user || u.name_clean === user)
	}
}
