package utils

import scala.concurrent.duration._

object LazyCollection extends Collector[LazyCollection[_, _]] {
	/**
	 * Constructor helper
	 */
	def apply[K, T](generator: (K) => T, expire: FiniteDuration) = {
		new LazyCollection[K, T](generator, expire)
	}
}

class LazyCollection[K, T] private(generator: (K) => T, expire: FiniteDuration) extends Collectable {
	/**
	 * Register in global registry
	 */
	LazyCollection.register(this)

	/**
	 * Every cells from this collection
	 */
	private var cells = Map[K, LazyCache[T]]()

	/**
	 * Fetch or create a cell for a given key
	 */
	def apply(key: K): T = {
		cells.getOrElse(key, {
			this.synchronized {
				val new_cell = LazyCache[T](expire)(generator(key))
				cells = cells.updated(key, new_cell)
				new_cell
			}
		}).value
	}

	/**
	 * Force expiration of a given cell
	 */
	def clear(key: K): Unit = for (cell <- cells.get(key)) cell.clear()

	/**
	 * Collect undefined cell to free memory
	 */
	def collect(): Unit = this.synchronized {
		cells = cells.filter { case (key, cell) => cell.hasValue }
	}
}