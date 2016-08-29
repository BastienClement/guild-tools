package models

import utils.SlickAPI._

class Toons(tag: Tag) extends Table[Toon](tag, "gt_chars") {
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
	def spec = column[Int]("spec")
	def failures = column[Int]("failures")
	def invalid = column[Boolean]("invalid")
	def last_update = column[Long]("last_update")

	def * = (id, name, server, owner, main, active, klass, race, gender, level, achievements, thumbnail, ilvl, spec, invalid, last_update) <> ((Toon.apply _).tupled, Toon.unapply)
}

object Toons extends TableQuery(new Toons(_)) {
	def findById(id: Rep[Int]) = this.filter(_.id === id)
}
