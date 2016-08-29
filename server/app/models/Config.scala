package models

import utils.SlickAPI._

case class Config(user: Int, data: String)

class Configs(tag: Tag) extends Table[Config](tag, "gt_config") {
	def user = column[Int]("user", O.PrimaryKey)
	def data = column[String]("data")

	def * = (user, data) <> (Config.tupled, Config.unapply)
}

object Configs extends TableQuery(new Configs(_))
