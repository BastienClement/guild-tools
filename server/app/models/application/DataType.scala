package models.application

import boopickle.DefaultBasic._
import utils.SlickAPI._

/**
  * Defines the encoding of the application data in the database.
  */
sealed trait DataType
object DataType {
	/** There is no data available. */
	case object NoData extends DataType

	/** The data is the JSON encoded form */
	case object JsonData extends DataType

	/** Converts a DataType to its numeric representation for storage in the database. */
	def toInt(t: DataType): Int = t match {
		case NoData => 0
		case JsonData => 1
	}

	/** Returns the DataType corresponding to the numeric value stored in the database. */
	def fromInt(i: Int): DataType = i match {
		case 0 => NoData
		case 1 => JsonData
	}

	/** ColumnType definition for Slick */
	implicit val DataTypeColumnType = MappedColumnType.base[DataType, Int](toInt, fromInt)

	/** Automatic Pickling serialization for DataType instances */
	implicit val DataTypePickler = transformPickler[DataType, Int](fromInt)(toInt)
}
