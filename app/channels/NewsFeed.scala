package channels

import gtp3._
import models._
import models.mysql._
import reactive._
import utils.{LazyCache, SmartTimestamp}
import scala.concurrent.duration._

object NewsFeed extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new NewsFeed)

	// List of open news feed channels
	private var open = Set[NewsFeed]()

	// Update every connected client
	def ping() = {
		cache.clear()
		for (f <- open) f.update()
	}

	def cache = LazyCache(5.minutes) {
		Feeds.sortBy(_.time.desc).take(50).run.await
	}
}

class NewsFeed extends ChannelHandler with InitHandler with CloseHandler {
	def init() = {
		NewsFeed.open += this
		update()
	}

	def update() = channel.send("update", NewsFeed.cache.value)
	def close() = NewsFeed.open -= this
}
