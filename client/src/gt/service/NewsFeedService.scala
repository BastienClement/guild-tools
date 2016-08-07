package gt.service

import gt.service.base.Service

object NewsFeedService extends Service {
	val channel = registerChannel("newsfeed")
}
