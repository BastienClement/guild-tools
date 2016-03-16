package models

import java.sql.Timestamp
import models.mysql._
import utils.SmartTimestamp

/**
  * Profile data without Option
  */
case class ProfileData(user: Int, realname: String, btag: String, phone: String, birthday: String,
                       mail: String, location: String)

/**
  * Profile row
  */
case class Profile(user: Int,
                   realname: Option[String], btag: Option[String], phone: Option[String],
                   birthday: Option[Timestamp], mail: Option[String], location: Option[String],
                   realname_visibility: Int = 0, btag_visibility: Int = 0, phone_visibility: Int = 0,
                   birthday_visibility: Int = 0, mail_visibility: Int = 0, location_visibility: Int = 0
                  ) {
	@deprecated("Use concealFor", "2016-03-16")
	def conceal = copy(phone = None, mail = None)

	/**
	  * Conceals information according to a given user access level.
	  *
	  * @param user The user requesting the profile
	  */
	def concealFor(user: Option[User]): Profile = {
		// Required visibility value
		val required = user match {
			case Some(u) if u.id == this.user => -1
			case Some(u) if u.promoted => 0
			case Some(u) if u.member => 1
			case Some(u) if u.fs => 2
			case _ => 3
		}

		// Filter fields according to visibility level of the viewer
		def filter[T](opt: Option[T], visibility: Int) = opt.filter(_ => visibility >= required)

		copy(
			realname = filter(realname, realname_visibility),
			btag = filter(btag, btag_visibility),
			phone = filter(phone, phone_visibility),
			birthday = filter(birthday, birthday_visibility),
			mail = filter(mail, mail_visibility),
			location = filter(location, location_visibility)
		)
	}

	/**
	  * Conceals information according to a given user access level.
	  *
	  * @param user The user requesting the profile
	  */
	def concealFor(user: User): Profile = concealFor(Some(user))

	/**
	  * Extracts data from the profile object and replace missing values with placeholders.
	  * Note that the birthday is converted from Timestamp to String.
	  */
	def withPlaceholders = ProfileData(user,
		realname.getOrElse("–"), btag.getOrElse("–"), phone.getOrElse("–"),
		birthday.map(SmartTimestamp.iso.format(_)).getOrElse("–"),
		mail.getOrElse("–"), location.getOrElse("–"))
}

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

	def birthday = column[Option[Timestamp]]("birthday")
	def birthday_visibility = column[Int]("birthday_visibility")

	def mail = column[Option[String]]("mail")
	def mail_visibility = column[Int]("mail_visibility")

	def location = column[Option[String]]("location")
	def location_visibility = column[Int]("location_visibility")

	def * = (user, realname, battletag, phone, birthday, mail, location,
		realname_visibility, battletag_visibility, phone_visibility, birthday_visibility,
		mail_visibility, location_visibility) <> (Profile.tupled, Profile.unapply)
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
