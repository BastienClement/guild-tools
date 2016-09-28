package models

import boopickle.DefaultBasic._
import utils.annotation.data

@data case class StreamStatus(user: Int, live: Boolean, progress: Boolean, viewersIds: Iterable[Int])

object StreamStatus {
	implicit val StreamStatusPickler = PicklerGenerator.generatePickler[StreamStatus]
}
