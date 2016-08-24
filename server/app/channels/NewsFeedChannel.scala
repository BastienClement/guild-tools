package channels

import akka.actor.{ActorRef, Props}
import boopickle.DefaultBasic._
import gtp3._
import models._
import models.mysql._
import scala.concurrent.duration._
import utils.CacheCell

object NewsFeedChannel extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new NewsFeedChannel))

	// List of open news feed channels
	private var open_feeds = Set[ActorRef]()

	// Update every connected client
	def ping() = {
		cache.clear()
		for (f <- open_feeds) f ! Update
	}

	def cache = CacheCell(5.minutes) {
		NewsFeed.sortBy(_.link.desc).take(50).run.await
	}

	case object Update
}

class NewsFeedChannel extends ChannelHandler {
	init {
		NewsFeedChannel.open_feeds.synchronized {
			NewsFeedChannel.open_feeds += self
			update()
		}
	}

	stop {
		NewsFeedChannel.open_feeds.synchronized {
			NewsFeedChannel.open_feeds -= self
		}
	}

	akka {
		case NewsFeedChannel.Update => update()
	}

	def update() = send("update", NewsFeedChannel.cache.value)
}
