package models

import models.mysql._
import utils.DateTime

/**
  * Profile table
  */
class Profiles(tag: Tag) extends Table[Profile](tag, "gt_profiles") {
	def user = column[Int]("id", O.PrimaryKey)

	def realname = column[Option[String]]("realname")
	def realname_visibility = column[Int]("realname_visibility")

	def battletag = column[Option[String]]("battletag")
	def battletag_visibility = column[Int]("battletag_visibility")

	def phone = column[Option[String]]("phone")
	def phone_visibility = column[Int]("phone_visibility")

	def birthday = column[Option[DateTime]]("birthday")
	def birthday_visibility = column[Int]("birthday_visibility")

	def mail = column[Option[String]]("mail")
	def mail_visibility = column[Int]("mail_visibility")

	def location = column[Option[String]]("location")
	def location_visibility = column[Int]("location_visibility")

	def * = (user, realname, battletag, phone, birthday, mail, location,
			realname_visibility, battletag_visibility, phone_visibility, birthday_visibility,
			mail_visibility, location_visibility) <> ((Profile.apply _).tupled, Profile.unapply)
}

/**
  * Query helpers
  */
object Profiles extends TableQuery(new Profiles(_)) {
	/**
	  * Queries Profile based on the user's ID
	  */
	def findById(user: Rep[Int]) = Profiles.filter(_.user === user)

	/**
	  * An empty profile used in case Option[ProfileData] is None.
	  */
	val empty = ProfileData(-1, "–", "–", "–", "–", "–", "–")
}
