package models.authentication

import models.User
import models.mysql._

class Users(tag: Tag) extends Table[User](tag, "gt_users") {
	def id = column[Int]("id", O.PrimaryKey)
	def name = column[String]("name")
	def group = column[Int]("group")

	def password = column[String]("password")
	def name_clean = column[String]("name_clean")

	def * = (id, name, group) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_))

