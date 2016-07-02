package models.application

import models.mysql._
import play.api.libs.json.{Format, JsValue, Json}
import scala.language.implicitConversions

/**
  * An application stage.
  */
sealed class Stage private (val id: Int, val name: String)

object Stage {
	/** Application was just posted, awaiting validation from an officer */
	case object Pending extends Stage(0, "Pending")

	/** Applicant not yet guilded, members can review the application and post messages */
	case object Review extends Stage(1, "Review")

	/** Applicant is in trial period */
	case object Trial extends Stage(2, "Trial")

	/** The application was accepted, archived */
	case object Accepted extends Stage(3, "Accepted")

	/** The application was refused, archived */
	case object Refused extends Stage(4, "Refused")

	/** The application was previously accepted, but the applicant is no longer playing */
	case object Archived extends Stage(5, "Archived")

	/** The application is spam */
	case object Spam extends Stage(6, "Spam")

	/** Return the corresponding Stage object for the given stage identifier. */
	def fromId(id: Int): Stage = id match {
		case 0 => Pending
		case 1 => Review
		case 2 => Trial
		case 3 => Accepted
		case 4 => Refused
		case 5 => Archived
		case 6 => Spam
		case _ => throw new NoSuchElementException(s"No application stage is defined for identifier $id")
	}

	/** Extract the stage identifier from a Stage instance. */
	def toId(stage: Stage): Int = stage.id

	/** Automatic mapping from Stage instances to an Int column in database */
	implicit val StageColumnType = MappedColumnType.base[Stage, Int](toId, fromId)

	/** Automatic JSON serialization for Stage instances */
	implicit val StageJsonFormat = new Format[Stage] {
		def reads(json: JsValue) = json.validate[Int].map(fromId _)
		def writes(stage: Stage) = Json.toJson(stage.id)
	}
}
