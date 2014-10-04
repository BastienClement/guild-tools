package models

import mysql._

case class User(id: Int, name: String, group: Int, color: String)

case class PublicUser(id: Int, name: String, group: Int, color: String)

class Users(tag: Tag) extends Table[User](tag, "phpbb_users") {
	def id = column[Int]("user_id", O.PrimaryKey)
	def name = column[String]("username")
	def group = column[Int]("group_id")
	def color = column[String]("user_colour")

	def pass = column[String]("user_password")
	def name_clean = column[String]("username_clean")

	def * = (id, name, group, color) <> (User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_))
