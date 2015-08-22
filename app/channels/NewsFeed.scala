package channels

import gtp3._
import models._
import models.mysql._
import reactive._
import utils.SmartTimestamp

object NewsFeed extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(new NewsFeed)

	// List of open news feed channels
	private var open = Set[NewsFeed]()

	// Update every connected client
	def ping() = for (f <- open) f.update()
}

class NewsFeed extends ChannelHandler with InitHandler with CloseHandler {
	val handlers = Map[String, Handler]()

	def init() = {
		NewsFeed.open += this
		update()
	}

	def update() = for (news <- Feeds.sortBy(_.time.desc).take(50).run) {
		channel.send("update", news)
	}

	def close() = {
		NewsFeed.open -= this
	}
}
