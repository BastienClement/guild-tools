package model

import util.DateTime
import util.annotation.data

@data case class NewsFeedData(guid: String, source: String, title: String, link: String, time: DateTime, tags: String)
