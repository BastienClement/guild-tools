package channels

import akka.actor.{ActorRef, Props}
import gtp3._
import models._
import models.mysql._
import reactive._
import utils.{LazyCache, SmartTimestamp}
import scala.concurrent.duration._

object NewsFeed extends ChannelValidator {
	def open(request: ChannelRequest) = request.accept(Props(new NewsFeed))

	// List of open news feed channels
	private var open_feeds = Set[ActorRef]()

	// Update every connected client
	def ping() = {
		cache.clear()
		for (f <- open_feeds) f ! Update
	}

	def cache = LazyCache(5.minutes) {
		Feeds.sortBy(_.time.desc).take(50).run.await
	}

	case object Update
}

class NewsFeed extends ChannelHandler {
	init {
		NewsFeed.open_feeds.synchronized {
			NewsFeed.open_feeds += self
			update()
		}
	}

	stop {
		NewsFeed.open_feeds.synchronized {
			NewsFeed.open_feeds -= self
		}
	}

	akka {
		case NewsFeed.Update => update()
	}

	def update() = send("update", NewsFeed.cache.value)
}
