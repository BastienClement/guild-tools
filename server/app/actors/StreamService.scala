package actors

import actors.StreamService._
import data.UserGroups
import models.live.Streams
import models.{PhpBBUsers, User}
import reactive._
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.concurrent.duration.{Deadline, _}
import scala.concurrent.{Await, Future}
import utils.Implicits._
import utils.SlickAPI._
import utils._

private[actors] class StreamServiceImpl extends StreamService

object StreamService extends StaticActor[StreamService, StreamServiceImpl]("StreamService") {
	/**
	  * A streaming ticket.
	  * Users are required to request a ticket to access a stream.
	  * The process of obtaining a ticket allows stream access to be controlled.
	  */
	case class Ticket(id: String, user: User, stream: String, expire: Deadline)

	/**
	  * A stream viewer.
	  */
	case class Viewer(user: User, remote: String)

	/**
	  * Stream events.
	  */
	object Events extends PubSub[User] {
		/**
		  * Stream information updated.
		  */
		case class StreamNotify(stream: ActiveStream)

		/**
		  * A stream is now inactive and should be removed form UIs.
		  */
		case class StreamInactive(stream: ActiveStream)
	}

	/**
	  * Manages the list of active streams.
	  */
	object StreamList {
		/**
		  * List of currently active streams.
		  */
		private val actives = TrieMap[String, ActiveStream]()

		/**
		  * Check if a given stream id is currently active.
		  */
		def isActive(stream_id: String) = actives.contains(stream_id)

		/**
		  * Returns the stream associated with an id.
		  */
		def get(stream_id: String) = actives.get(stream_id)

		/**
		  * A stream started being published.
		  */
		def startPublishing(stream_id: String) = this.synchronized {
			actives.getOrElseUpdate(stream_id, {
				val meta = Await.result(Streams.filter(_.token === stream_id).head, 15.seconds)
				ActiveStream(meta)
			}).startPublishing()
		}

		/**
		  * A stream stopped being published.
		  */
		def stopPublishing(stream_id: String) = {
			for (stream <- actives.get(stream_id)) {
				stream.stopPublishing()
			}
		}

		/**
		  * A stream is now inactive.
		  * Removes it from the list.
		  */
		def remove(stream_id: String) = this.synchronized {
			actives.remove(stream_id)
		}

		/**
		  * Return the list of currently actives streams.
		  */
		def getList = actives.values
	}

	/**
	  * A currently active stream
	  */
	case class ActiveStream(var meta: models.live.Stream, var live: Boolean = false, viewers: TrieMap[Int, Viewer] = TrieMap()) {
		/**
		  * List of viewers user_ids
		  */
		def viewersIds = viewers.values.map(_.user.id)

		/**
		  * Returns the number of currently active viewers.
		  */
		def viewersCount = viewers.size

		/**
		  * Send StreamNotify event.
		  * This event is emitted when anything about the
		  * stream is updated
		  */
		def sendNotify() = Events !# Events.StreamNotify(this)

		/**
		  * Send the StreamInactive event.
		  * This event is emitted once the stream is offline and
		  * has no more viewers.
		  */
		def sendInactive() = {
			Events !# Events.StreamInactive(this)
			StreamList.remove(meta.token)
		}

		/**
		  * Refreshes meta informations about the stream
		  */
		def refresh() = {
			for (stream <- Streams.filter(_.token === meta.token).head if meta != stream) {
				meta = stream
				sendNotify()
			}
		}

		/**
		  * The stream started being published.
		  */
		def startPublishing() = if (!live) {
			live = true
			sendNotify()
		}

		/**
		  * The stream stopped being published.
		  */
		def stopPublishing() = if(live) {
			live = false
			if (viewersCount < 1) sendInactive()
			else sendNotify()
		}

		/**
		  * A new client is now watching the stream.
		  */
		def startWatching(client: Int, user: User, remote: String) = {
			viewers.put(client, Viewer(user, remote))
			sendNotify()
		}

		/**
		  * A client stopped watching the stream.
		  */
		def stopWatching(client: Int) = {
			for (viewer <- viewers.remove(client)) {
				if (!live && viewersCount < 1) sendInactive()
				else sendNotify()
			}
		}

		/**
		  * Returns details for a given client id.
		  */
		def clientDetails(client: Int) = viewers.get(client)
	}

	/**
	  * The collector object for the TicketsCollection.
	  */
	object TicketCollector extends Collector[TicketsCollection.type]

	/**
	  * A Collection of streaming tickets.
	  */
	object TicketsCollection extends Collectable {
		// Enable collector
		TicketCollector.register(this)

		private val tickets = TrieMap[String, Ticket]()

		/**
		  * Returns the ticket matching the given id.
		  */
		def get(id: String) = tickets.get(id)

		/**
		  * Adds a new ticket for a specific id.
		  */
		def put(id: String, ticket: Ticket) = tickets.put(id, ticket)

		/**
		  * Removes a ticket from the collection.
		  */
		def remove(id: String) = tickets.remove(id)

		/**
		  * Removes expired tickets from the collection.
		  */
		def collect(): Unit = this.synchronized {
			for ((id, ticket) <- tickets) {
				if (ticket.expire.isOverdue) tickets.remove(id)
			}
		}
	}
}

/**
  * Streaming service provider.
  */
trait StreamService {
	/**
	  * List of currently active viewers.
	  * If a user's id is in this set, he will not be able
	  * to access another stream simultaneously.
	  */
	private val viewers = mutable.Set[Int]()

	/**
	  * List of whitelisted users able to access progress
	  * protected streams.
	  */
	private val whitelist = mutable.Set[Int]()

	/**
	  * Cache of every stream from guild users.
	  */
	private def streams_list = {
		val query = for {
			user <- PhpBBUsers if user.group inSet UserGroups.roster
			stream <- Streams if stream.user === user.id
		} yield stream

		for (streams <- query.run) yield {
			for (stream <- streams) yield {
				StreamList.get(stream.token) match {
					case Some(active) => active
					case None => ActiveStream(stream, false, TrieMap.empty)
				}
			}
		}
	}

	/**
	  * Creates a new Streaming ticket.
	  * Requires that the stream can be watched by the user.
	  */
	def createTicket(owner_id: Int, user: User): Future[Ticket] = {
		if (!user.promoted && viewers.contains(user.id)) {
			StacklessException("You are not allowed to watch multiple streams at the same time.")
		} else {
			for {
				stream <- Streams.filter(_.user === owner_id).head.otherwise("The requested stream does not exists.")
			} yield {
				if (stream.progress && !whitelist.contains(user.id))
					throw new Exception("Access to this stream is currently restricted.")

				if (!StreamList.isActive(stream.token))
					throw new Exception("The requested stream is currently offline.")

				val ticket_id = utils.randomToken()
				val ticket = Ticket(ticket_id, user, stream.token, 15.seconds.fromNow)

				TicketsCollection.put(ticket_id, ticket)
				ticket
			}
		}
	}

	/**
	  * Consume a ticket and access the linked stream.
	  */
	def consumeTicket(id: String) = AsFuture {
		val ticket = TicketsCollection.remove(id).filter(t => t.expire.hasTimeLeft)
		ticket match {
			case Some(t) if t.user.promoted || !viewers.contains(t.user.id) => t
			case None => throw new Exception("Invalid ticket")
		}
	}

	/**
	  * Changes the stream visibility setting.
	  */
	def changeVisibility(user: Int, limited: Boolean) = {
		for {
			updated <- Streams.filter(_.user === user).map(_.progress).update(limited).run if updated > 0
			stream <- Streams.filter(_.user === user).head
		} yield {
			StreamList.get(stream.token) match {
				case Some(active) => active.refresh()
				case None => // The stream is not active, no need to refresh anything
			}
		}
	}

	/**
	  * A user started watching a stream.
	  */
	def play(stream_id: String, user: User, remote: String, client: Int): Unit = {
		viewers += user.id
		for (stream <- StreamList.get(stream_id)) {
			stream.startWatching(client, user, remote)
		}
	}

	/**
	  * A user stopped watching a stream.
	  */
	def stop(stream_id: String, client: Int): Unit = {
		for (stream <- StreamList.get(stream_id)) {
			for (viewer <- stream.clientDetails(client)) {
				viewers -= viewer.user.id
			}
			stream.stopWatching(client)
		}
	}

	/**
	  * A live stream started.
	  */
	def publish(stream: String) = StreamList.startPublishing(stream)

	/**
	  * A live stream ended.
	  */
	def unpublish(stream: String) = StreamList.stopPublishing(stream)

	/**
	  * Returns the list of actives streams ids.
	  */
	def listActiveStreams() = AsFuture {
		//streams_list
		StreamList.getList
	}
}
