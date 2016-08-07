package model

import util.DateTime
import util.annotation.data

/**
  * Profile data without Option
  */
@data case class ProfileData(user: Int, realname: String, btag: String, phone: String, birthday: String,
                             mail: String, location: String)

/**
  * Profile row
  */
@data case class Profile(user: Int,
                         realname: Option[String], btag: Option[String], phone: Option[String],
                         birthday: Option[DateTime], mail: Option[String], location: Option[String],
                         realname_visibility: Int = 0, btag_visibility: Int = 0, phone_visibility: Int = 0,
                         birthday_visibility: Int = 0, mail_visibility: Int = 0, location_visibility: Int = 0
                        ) {
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
	def withPlaceholders = {
		ProfileData(user,
			realname.getOrElse("–"), btag.getOrElse("–"), phone.getOrElse("–"),
			birthday.map(dt => dt.toISOString).getOrElse("–"),
			mail.getOrElse("–"), location.getOrElse("–"))
	}
}
