package models

import models.mysql._
import play.api.libs.json.Json

case class Config(user: Int, data: String) {
	lazy val jsvalue = Json.parse(data)
}

class Configs(tag: Tag) extends Table[Config](tag, "gt_config") {
	def user = column[Int]("user", O.PrimaryKey)
	def data = column[String]("data")

	def * = (user, data) <> (Config.tupled, Config.unapply)
}

object Configs extends TableQuery(new Configs(_)) {
	val developer_users = Set(1647)
	val officier_groups = Set(11)
}
