package channels

import akka.actor.{ActorRef, Props}
import boopickle.DefaultBasic._
import gtp3._
import models.NewsFeed
import scala.concurrent.Await
import scala.concurrent.duration._
import utils.CacheCell
import utils.SlickAPI._

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
		Await.result(NewsFeed.sortBy(_.time.desc).take(50).run, 10.seconds)
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
