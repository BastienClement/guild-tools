package models

import actors.Actors.RosterManager
import models.mysql._

case class Char(
		id: Int,
		name: String,
		server: String,
		owner: Int,
		main: Boolean,
		active: Boolean,
		`class`: Int,
		race: Int,
		gender: Int,
		level: Int,
		achievements: Int,
		thumbnail: String,
		ilvl: Int,
		role: String,
		last_update: Long) {
	val clazz = `class`

	if (!Chars.validateRole(role)) {
		throw new Exception("Invalid role value")
	}
}

class Chars(tag: Tag) extends Table[Char](tag, "gt_chars") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def name = column[String]("name")
	def server = column[String]("server")
	def owner = column[Int]("owner")
	def main = column[Boolean]("main")
	def active = column[Boolean]("active")
	def klass = column[Int]("class")
	def race = column[Int]("race")
	def gender = column[Int]("gender")
	def level = column[Int]("level")
	def achievements = column[Int]("achievements")
	def thumbnail = column[String]("thumbnail")
	def ilvl = column[Int]("ilvl")
	def role = column[String]("role")
	def last_update = column[Long]("last_update")

	def * = (id, name, server, owner, main, active, klass, race, gender, level, achievements, thumbnail, ilvl, role, last_update) <> (Char.tupled, Char.unapply)
}

object Chars extends TableQuery(new Chars(_)) {
	def notifyCreate(char: Char): Unit = {
		RosterManager.updateChar(char)
	}

	def notifyUpdate(char: Char): Unit = {
		RosterManager.updateChar(char)
	}

	def notifyDelete(id: Int): Unit = {
		RosterManager.deleteChar(id)
	}

	def validateRole(role: String): Boolean = role match {
		case "TANK" | "HEALING" | "DPS" => true
		case _ => false
	}
}
