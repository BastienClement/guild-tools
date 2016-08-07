package model

import data.UserGroups
import util.annotation.data

@data case class User(id: Int, name: String, group: Int) {
	lazy val developer = UserGroups.developers.contains(id)
	lazy val officer = UserGroups.officers.contains(group)
	lazy val promoted = developer || officer
	lazy val member = promoted || UserGroups.members.contains(group)
	lazy val roster = promoted || UserGroups.roster.contains(group)
	lazy val fs = UserGroups.fromscratch.contains(group)
}
