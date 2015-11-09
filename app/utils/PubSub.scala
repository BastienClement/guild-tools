package utils

import akka.actor.{Actor, ActorRef, Props, Terminated}
import play.api.Play.current
import play.api.libs.concurrent.Akka
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.language.existentials
import utils.PubSub._

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
					ps.unsubscribe(actor)
					actors -= actor
				}
		}
	}

	private val Watcher = Akka.system.actorOf(Props[WatcherActor], "PubSubWatcher")
}

trait PubSub[A] {
	// Subscribers
	private val subs = TrieMap[ActorRef, A]()

	// Sub
	def subscribe(actor: ActorRef, data: A): Unit = {
		if (subs.putIfAbsent(actor, data).isEmpty) {
			Watcher ! Watch(this, actor)
		}
	}

	final def subscribe(data: A)(implicit actor: ActorRef): Unit = subscribe(actor, data)

	// Unsub
	def unsubscribe(actor: ActorRef): Unit = subs.remove(actor)
	final def unsubscribe($dummy: Unit = ())(implicit actor: ActorRef): Unit = unsubscribe(actor)

	// Internal publishing loop
	private def publish(msg: Any, s: Iterable[ActorRef]): Unit = for (sub <- s) sub ! msg

	// Publish to all subs
	def publish(msg: Any): Unit = publish(msg, subs.keys)
	def !#(msg: Any): Unit = publish(msg, subs.keys)

	// Publish to subs with data matching a filter function
	def publish(msg: Any, filter: (A) => Boolean): Unit =
		publish(msg, for ((actor, data) <- subs if filter(data)) yield actor)
}
