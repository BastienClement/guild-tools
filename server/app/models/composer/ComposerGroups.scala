package models.composer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import slick.lifted.TableQuery
import utils.PubSub
import utils.SlickAPI._

class ComposerGroups(tag: Tag) extends Table[ComposerGroup](tag, "gt_composer_doc_groups") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def doc = column[Int]("doc")
	def title = column[String]("title")

	def * = (id, doc, title) <> ((ComposerGroup.apply _).tupled, ComposerGroup.unapply)
}

object ComposerGroups extends TableQuery(new ComposerGroups(_)) with PubSub[Unit] {
	case class Updated(group: ComposerGroup)
	case class Deleted(id: Int)

	private val insertQuery = {
		ComposerGroups returning ComposerGroups.map(_.id) into ((grp, id) => grp.copy(id = id))
	}

	def findById(id: Int) = ComposerGroups.filter(_.id === id)

	def create(doc: Int, title: String): Unit = {
		for (doc <- (insertQuery += ComposerGroup(0, doc, title)).run) {
			publish(Updated(doc))
		}
	}

	def rename(id: Int, name: String): Unit = {
		(for {
			n <- findById(id).map(_.title).update(name) if n > 0
			grp <- findById(id).result.head
		} yield grp).run.andThen {
			case Success(group) => publish(Updated(group))
		}
	}

	def delete(id: Int): Unit = {
		for (n <- findById(id).filter(_.id === id).delete.run if n > 0) {
			publish(Deleted(id))
		}
	}
}
