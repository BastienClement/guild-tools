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
		cells = cells.filter { case (key, cell) => !cell.isExpired }
	}
}
