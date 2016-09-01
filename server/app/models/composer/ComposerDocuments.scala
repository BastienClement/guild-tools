package models.composer

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Success
import slick.lifted.TableQuery
import utils.PubSub
import utils.SlickAPI._

class ComposerDocuments(tag: Tag) extends Table[ComposerDocument](tag, "gt_composer_docs") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def title = column[String]("title")
	def style = column[String]("style")

	def * = (id, title, style) <> ((ComposerDocument.apply _).tupled, ComposerDocument.unapply)
}

object ComposerDocuments extends TableQuery(new ComposerDocuments(_)) with PubSub[Unit] {
	case class Updated(document: ComposerDocument)
	case class Deleted(id: Int)

	private val insertQuery = {
		ComposerDocuments returning ComposerDocuments.map(_.id) into ((doc, id) => doc.copy(id = id))
	}

	def findById(id: Int) = ComposerDocuments.filter(_.id === id)

	def create(title: String, style: String): Unit = {
		for (doc <- (insertQuery += ComposerDocument(0, title, style)).run) {
			publish(Updated(doc))
		}
	}

	def rename(id: Int, name: String): Unit = {
		(for {
			n <- findById(id).map(_.title).update(name) if n > 0
			doc <- findById(id).result.head
		} yield doc).run.andThen {
			case Success(doc) => publish(Updated(doc))
		}
	}

	def delete(id: Int): Unit = {
		for (n <- findById(id).filter(_.id == id).delete.run if n > 0) {
			publish(Deleted(id))
		}
	}
}
