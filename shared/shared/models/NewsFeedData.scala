package models

import utils.DateTime
import utils.annotation.data

@data case class NewsFeedData(guid: String, source: String, title: String, link: String, time: DateTime, tags: String)
