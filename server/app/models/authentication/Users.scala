package models.authentication

import models.{User, _}
import reactive.ExecutionContext
import utils.SlickAPI._
import utils.crypto.BCrypt

class Users(tag: Tag) extends Table[User](tag, "gt_users") {
	def id = column[Int]("id", O.PrimaryKey)
	def name = column[String]("name")
	def group = column[Int]("group")

	def password = column[String]("password")
	def name_clean = column[String]("name_clean")

	def * = (id, name, group) <> ((User.apply _).tupled, User.unapply)
}

object Users extends TableQuery(new Users(_)) {
	/**
	  * Queries user based on username.
	  * The username is converted to lower-case and checked against both
	  * name and name_clean.
	  *
	  * @param username The user's name
	  */
	def findByUsername(username: Rep[String]) = {
		val user = username.toLowerCase
		Users.filter(u => u.name.toLowerCase === user || u.name_clean === user).filter(_.password =!= "")
	}

	/**
	  * Upgrades a legacy phpBB account to a GuildTools one.
	  * This function will copy some data from the phpBB table and populate the GT one.
	  *
	  * @param userid   The user's ID
	  * @param raw_pass The plain text user password.
	  *                 Required to compute the stronger bcrypt password
	  */
	def upgradeAccount(userid: Int, raw_pass: String) = {
		val old_account = PhpBBUsers.filter(_.id === userid).map(u => (u.name, u.group, u.name_clean))

		for ((name, group, name_clean) <- old_account.head) {
			val upgrade = Users.map(u => (u.id, u.name, u.group, u.password, u.name_clean)).insertOrUpdate {
				(userid, name, group, BCrypt.hashpw(raw_pass, BCrypt.gensalt()), name_clean)
			}
			upgrade.run
		}
	}
}

