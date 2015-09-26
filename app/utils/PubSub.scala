package utils

import akka.actor.{Actor, ActorRef, Props, Terminated}
import play.api.Play.current
import play.api.libs.concurrent.Akka
import utils.PubSub._

import scala.collection.mutable

object PubSub {
	private case class Watch(ps: PubSub[_], actor: ActorRef)
	private case class Unwatch(ps: PubSub[_], actor: ActorRef)

	private class WatcherActor extends Actor {
		private val watching = mutable.WeakHashMap[PubSub[_], mutable.Set[ActorRef]]()
		def receive = {
			case Watch(ps, actor) =>
				watching.getOrElseUpdate(ps, mutable.Set()).add(actor)
				context.watch(actor)

			case Terminated(actor) =>
				for ((ps, actors) <- watching.find { case (p, a) => a.contains(actor) }) {
					if (ps hasSubscriber actor) ps.unsubscribe(actor)
					actors -= actor
				}
		}
	}

	private val Watcher = Akka.system.actorOf(Props[WatcherActor], "PubSubWatcher")
}

trait PubSub[A] {
	// Subscribers
	private var subs = Map[ActorRef, A]()
	private def hasSubscriber(actor: ActorRef) = subs.contains(actor)

	// Sub
	def subscribe(actor: ActorRef, data: A): Unit = {
		subs.synchronized {
			subs += actor -> data
		}
		Watcher ! Watch(this, actor)
	}

	final def subscribe(data: A)(implicit actor: ActorRef): Unit = subscribe(actor, data)

	// Unsub
	def unsubscribe(actor: ActorRef): Unit = subs.synchronized {
		subs -= actor
	}

	final def unsubscribe($dummy: Unit = ())(implicit actor: ActorRef): Unit = {
		if (this hasSubscriber actor) unsubscribe(actor)
	}

	// Internal publishing loop
	private def publish(msg: Any, s: Iterable[ActorRef]): Unit = for (sub <- s) sub ! msg

	// Publish to all subs
	protected def publish(msg: Any): Unit = publish(msg, subs.keys)
	final protected def !#(msg: Any): Unit = publish(msg, subs.keys)

	// Publish to subs with data matching a filter function
	protected def publish(msg: Any)(filter: (A) => Boolean): Unit =
		publish(msg, for ((actor, data) <- subs if filter(data)) yield actor)
}
