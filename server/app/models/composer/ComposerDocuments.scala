package models.composer

import models._
import models.mysql._
import scala.concurrent.ExecutionContext.Implicits.global
import utils.PubSub

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

	def create(title: String, style: String): Unit = {
		for (doc <- (insertQuery += ComposerDocument(0, title, style)).run) {
			publish(Updated(doc))
		}
	}
}
