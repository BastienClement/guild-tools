package utils

import akka.actor.Cancellable
import gt.Global.ExecutionContext

import scala.collection.mutable
import scala.concurrent.duration._

object LazyCollection {
	/**
	 * All LazyCollection instances currently living
	 */
	private val instances = mutable.WeakHashMap[LazyCollection[_, _], Unit]()

	/**
	 * Track garbage collector status
	 */
	private var collector_started = false
	private var collector: Cancellable = _

	/**
	 * Register a new collection in global registry and start garbage collection
	 */
	def register(col: LazyCollection[_, _]) = this.synchronized {
		instances(col) = ()
		if (!collector_started) {
			collector_started = true
			collector = scheduler.schedule(1.minute, 1.minute) {
				if (instances.size > 0) {
					instances.map(_._1.collect())
				} else {
					collector.cancel()
					collector_started = false
				}
			}
		}
	}

	/**
	 * Constructor helper
	 */
	def apply[K, T](generator: (K) => T, expire: FiniteDuration) = {
		new LazyCollection[K, T](generator, expire)
	}
}

class LazyCollection[K, T] private(generator: (K) => T, expire: FiniteDuration) {
	/**
	 * Register in global registry
	 */
	LazyCollection.register(this)

	/**
	 * Every cells from this collection
	 */
	private var cells = Map[K, LazyCell[T]]()

	/**
	 * Fetch or create a cell for a given key
	 */
	def apply(key: K): T = {
		cells.getOrElse(key, {
			this.synchronized {
				val new_cell = LazyCell[T](expire)(generator(key))
				cells = cells.updated(key, new_cell)
				new_cell
			}
		}).get
	}

	/**
	 * Force expiration of a given cell
	 */
	def clear(key: K): Unit = cells.get(key).map(_.clear())

	/**
	 * Collect undefined cell to free memory
	 */
	def collect(): Unit = this.synchronized {
		cells = cells.filter { case (key, cell) => cell.defined }
	}
}
