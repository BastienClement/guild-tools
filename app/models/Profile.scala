package models

import java.sql.Timestamp
import models.mysql._

case class Profile(user: Int, realname: Option[String], btag: Option[String], phone: Option[String],
                   birthday: Option[Timestamp], mail: Option[String], location: Option[String]) {
	def conceal = copy(phone = None, mail = None)
}

class Profiles(tag: Tag) extends Table[Profile](tag, "gt_profiles") {
	def user = column[Int]("id", O.PrimaryKey)
	def realname = column[Option[String]]("realname")
	def battletag = column[Option[String]]("battletag")
	def phone = column[Option[String]]("phone")
	def birthday = column[Option[Timestamp]]("birthday")
	def mail = column[Option[String]]("mail")
	def location = column[Option[String]]("location")

	def * = (user, realname, battletag, phone, birthday, mail, location) <> (Profile.tupled, Profile.unapply)
}

object Profiles extends TableQuery(new Profiles(_)) {
	def findById(user: Rep[Int]) = Profiles.filter(_.user === user)
}
