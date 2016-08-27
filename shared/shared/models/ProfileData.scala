package models

import utils.annotation.data

/**
  * Profile data without Option
  */
@data case class ProfileData(user: Int = -1, realname: String = "–", btag: String = "–", phone: String = "–",
                             birthday: String = "–", mail: String = "–", location: String = "–")
