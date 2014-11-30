package models

import models.mysql._

case class ComposerLockout(id: Int, title: String)

class ComposerLockouts(tag: Tag) extends Table[ComposerLockout](tag, "gt_composer_lockouts") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")

	def * = (id, title) <> (ComposerLockout.tupled, ComposerLockout.unapply)
}

object ComposerLockouts extends TableQuery(new ComposerLockouts(_))

// ----------------------------------------------------------------------------

case class ComposerGroup(id: Int, lockout: Int)

class ComposerGroups(tag: Tag) extends Table[ComposerGroup](tag, "gt_composer_groups") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def lockout = column[Int]("lockout")

	def * = (id, lockout) <> (ComposerGroup.tupled, ComposerGroup.unapply)
}

object ComposerGroups extends TableQuery(new ComposerGroups(_))

// ----------------------------------------------------------------------------

case class ComposerSlot(group: Int, char: Int, role: String)

class ComposerSlots(tag: Tag) extends Table[ComposerSlot](tag, "gt_composer_slots") {
	def group = column[Int]("group", O.PrimaryKey)
	def char = column[Int]("char", O.PrimaryKey)
	def role = column[String]("role")

	def * = (group, char, role) <> (ComposerSlot.tupled, ComposerSlot.unapply)
}

object ComposerSlots extends TableQuery(new ComposerSlots(_))
