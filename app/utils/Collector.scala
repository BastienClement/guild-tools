package utils

import akka.actor.Cancellable
import reactive.ExecutionContext
import scala.collection.mutable
import scala.concurrent.duration._

trait Collectable {
	def collect(): Unit
}

trait Collector[T <: Collectable] {
	/**
	 * All instances currently living
	 */
	private val instances = mutable.WeakHashMap[T, Unit]()

	/**
	 * Track garbage collector status
	 */
	private var collector_started = false
	private var collector: Cancellable = _

	/**
	 * Register a new item for garbage collection
	 */
	def register(col: T) = this.synchronized {
		instances(col) = ()
		if (!collector_started) {
			collector_started = true
			collector = scheduler.schedule(1.minute, 1.minute) {
				if (instances.nonEmpty) {
					instances.keys.foreach(_.collect())
				} else {
					collector.cancel()
					collector_started = false
				}
			}
		}
	}
}
