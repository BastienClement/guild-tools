package models

import boopickle.DefaultBasic._
import utils.DateTime
import utils.annotation.data

@data case class NewsFeedData(guid: String, source: String, title: String, link: String, time: DateTime, tags: String)

object NewsFeedData {
	implicit val NewsFeedDataPickler = PicklerGenerator.generatePickler[NewsFeedData]
}
