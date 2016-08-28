package utils

import akka.actor.{Actor, ActorRef, Props, Terminated}
import gt.GuildTools
import scala.collection.concurrent.TrieMap
import scala.collection.mutable
import scala.language.existentials
import utils.PubSub._

object PubSub {
	/**
	  * Begin watching an actor
	  *
	  * @param ps    PubSub object the actor was registered with
	  * @param actor Actor to watch
	  */
	private case class Watch(ps: PubSub[_], actor: ActorRef)

	/**
	  * Stop watching an actor
	  *
	  * @param ps    PubSub object the actor was removed from
	  * @param actor Actor to stop watching
	  */
	private case class Unwatch(ps: PubSub[_], actor: ActorRef)

	/**
	  * The PubSub watcher actor.
	  * A single instance of this actor will be instantiated for an instance of the application.
	  * When an actor is registered to a PubSub object, this must receive a message with the Actor
	  * and the corresponding PubSub object. The subscribed actor will be watched and automatically
	  * removed from the PubSub subscribers list when exiting.
	  */
	private class WatcherActor extends Actor {
		private val watching = mutable.WeakHashMap[PubSub[_], mutable.Set[ActorRef]]()
		def receive = {
			case Watch(ps, actor) =>
				watching.getOrElseUpdate(ps, mutable.Set()).add(actor)
				context.watch(actor)

			case Terminated(actor) =>
				for ((ps, actors) <- watching.filter { case (p, a) => a.contains(actor) }) {
					ps.unsubscribe(actor)
					actors -= actor
				}
		}
	}

	/**
	  * The Watcher instance
	  */
	private val Watcher = GuildTools.system.actorOf(Props[WatcherActor], "PubSubWatcher")

	/**
	  * A dummy object used to distinguish between same-signature method at runtime
	  */
	private[PubSub] trait OverloadCookie

	/**
	  * An implicit instance of the dummy object
	  */
	implicit object OverloadCookie extends OverloadCookie
}

/**
  * Mixin for actor-based PubSub messages delivery
  * This implementation is based on a TrieMap that is a lock-free thread-safe implementation
  * of a concurrent map.
  */
trait PubSub[A] {
	/**
	  * Subscribers
	  */
	private val subs = TrieMap[ActorRef, A]()

	/**
	  * Subscribe an actor to this feed
	  *
	  * @param actor Tee actor to deliver messages to
	  * @param data  Subscription data, this value will be available
	  *              when delivering messages to filter recipients
	  */
	def subscribe(actor: ActorRef, data: A): Unit = {
		if (subs.putIfAbsent(actor, data).isEmpty) {
			Watcher ! Watch(this, actor)
		}
	}

	/**
	  * Implicitly subscribe the current actor to this feed
	  *
	  * @param data  Subscription data
	  * @param actor The actor to deliver messages to
	  */
	final def subscribe(data: A)(implicit actor: ActorRef): Unit = subscribe(actor, data)

	/**
	  * TODO
	  * @param actor
	  * @param ev
	  */
	final def subscribe()(implicit actor: ActorRef, ev: Unit =:= A): Unit = subscribe(actor, ())

	/**
	  * Unsubscribe an actor from this feed
	  *
	  * @param actor The actor to remove
	  */
	def unsubscribe(actor: ActorRef): Unit = subs.remove(actor)

	/**
	  * Implicitly unsubscribe the current actor from this feed
	  *
	  * @param actor The actor to remove
	  * @param d     A dummy implicit object to distinguish this method at runtime,
	  *              automatically provided by the Scala compiler
	  */
	final def unsubscribe(implicit actor: ActorRef, d: OverloadCookie): Unit = unsubscribe(actor)

	/**
	  * Internal publishing loop
	  *
	  * @param msg The message to deliver
	  * @param s   A list of actor receiving the message
	  */
	private def publish(msg: Any, s: Iterable[ActorRef]): Unit = for (sub <- s) sub ! msg

	/**
	  * Publish to all subs
	  *
	  * @param msg The message to deliver
	  */
	def publish(msg: Any): Unit = publish(msg, subs.keys)

	/**
	  * Publish to all subs, alias for publish()
	  *
	  * @param msg The message to deliver
	  */
	def !#(msg: Any): Unit = publish(msg, subs.keys)

	/**
	  * Publish to subs with data matching a filter function
	  *
	  * @param msg    The message to deliver
	  * @param filter A predicate function taking the subscription data given for each actor
	  *               and returning if the associated actor should receive the message
	  */
	def publish(msg: Any, filter: (A) => Boolean): Unit = {
		publish(msg, for ((actor, data) <- subs if filter(data)) yield actor)
	}
}
