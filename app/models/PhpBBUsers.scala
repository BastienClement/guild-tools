package models

import actors.AuthService
import models.mysql._
import reactive.ExecutionContext
import scala.concurrent.Future

case class User(id: Int, name: String, group: Int) {
	lazy val developer = AuthService.developer_users.contains(id)
	lazy val officer = AuthService.officier_groups.contains(group)
	lazy val promoted = developer || officer
	lazy val member = promoted || AuthService.member_groups.contains(group)
	lazy val roster = promoted || AuthService.roster_groups.contains(group)
	lazy val fs = AuthService.fromscratch_groups.contains(group)

	def ready: Future[Boolean] = Chars.filter(_.owner === id).headOption.map(_.isDefined)
}

class PhpBBUsers(tag: Tag) extends Table[User](tag, "phpbb_users") {
	def id = column[Int]("user_id", O.PrimaryKey)
	def name = column[String]("username")
	def group = column[Int]("group_id")

	def pass = column[String]("user_password")
	def name_clean = column[String]("username_clean")

	def * = (id, name, group) <> (User.tupled, User.unapply)
}

object PhpBBUsers extends TableQuery(new PhpBBUsers(_)) {
	def findByUsername(username: Rep[String]) = {
		val user = username.toLowerCase
		PhpBBUsers.filter(u => u.name.toLowerCase === user || u.name_clean === user)
	}
}
