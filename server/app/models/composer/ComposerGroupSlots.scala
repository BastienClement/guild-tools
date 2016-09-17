package models.composer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import slick.lifted.TableQuery
import utils.PubSub
import utils.SlickAPI._

class ComposerGroupSlots(tag: Tag) extends Table[ComposerGroupSlot](tag, "gt_composer_groups_slots") {
	def group = column[Int]("group", O.PrimaryKey)
	def toon = column[Int]("toon", O.PrimaryKey)
	def tier = column[Int]("tier")
	def spec = column[Option[Int]]("spec")

	def * = (group, toon, tier, spec) <> ((ComposerGroupSlot.apply _).tupled, ComposerGroupSlot.unapply)
}

object ComposerGroupSlots extends TableQuery(new ComposerGroupSlots(_)) with PubSub[Unit] {
	case class Updated(slot: ComposerGroupSlot)
	case class Deleted(group: Int, toon: Int)

	def set(slot: ComposerGroupSlot): Unit = {
		this.insertOrUpdate(slot).run.onComplete {
			case Success(_) => publish(Updated(slot))
			case Failure(e) => // ignore ?
		}
	}

	def unset(group: Int, toon: Int): Unit = {
		for (count <- filter(s => s.group === group && s.toon === toon).delete.run) {
			if (count > 0) publish(Deleted(group, toon))
		}
	}
}
