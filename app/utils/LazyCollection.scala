package utils

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

object LazyCollection {
	val instances = mutable.WeakHashMap[LazyCollection[_, _], Unit]()
	def collect(): Unit = instances.map(_._1.collect())
}

class LazyCollection[K, T](generator: (K) => T, expire: FiniteDuration) {
	/**
	 * Register in global registry
	 */
	LazyCollection.instances.synchronized {
		LazyCollection.instances(this) = ()
	}

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
