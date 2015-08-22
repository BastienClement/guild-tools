package models

import gt.Global.ExecutionContext
import models.mysql._

import scala.concurrent.Future

case class User(id: Int, name: String, group: Int, color: String) {
	val developer = Users.developer_users.contains(id)
	val officer = Users.officier_groups.contains(group)
	val promoted = developer || officer

	def ready: Future[Boolean] = Chars.filter(_.owner === id).headOption.map(_.isDefined)
}

class Users(tag: Tag) extends Table[User](tag, "phpbb_users") {
	def id = column[Int]("user_id", O.PrimaryKey)
	def name = column[String]("username")
	def group = column[Int]("group_id")
	def color = column[String]("user_colour")

	def pass = column[String]("user_password")
	def name_clean = column[String]("username_clean")

	def * = (id, name, group, color) <>(User.tupled, User.unapply)
}

object Users extends TableQuery(new Users(_)) {
	val developer_users = Set(1647)
	val officier_groups = Set(11)
}
