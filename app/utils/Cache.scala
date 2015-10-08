package utils

import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.Failure

object Cache extends Collector[Cache[_, _]] {
	// Constructor helper
	def apply[K, T](expire: FiniteDuration)(generator: (K) => T) = {
		new Cache[K, T](expire)(generator)
	}

	// Construct an async LazyCollection
	// This version returns Future[T]s instead of Ts.
	// In adition, if the future is resolved with a failure, the
	// entry is deleted to recompute a new value on the next access
	def async[K, T](expire: FiniteDuration)(generator: (K) => Future[T]) = {
		lazy val col: Cache[K, Future[T]] = Cache(expire) { key =>
			generator(key) andThen {
				case Failure(_) => col.clear(key)
			}
		}
		col
	}
}

class Cache[K, T] private(expire: FiniteDuration)(generator: (K) => T) extends Collectable {
	// Register in global registry
	// This will allow the global collector to collect this collection
	Cache.register(this)

	// Cells from this collection
	private var cells = Map[K, CacheCell[T]]()

	// Fetch or create a cell for a given key
	// Thread-safe
	def apply(key: K): T = {
		cells.getOrElse(key, {
			this.synchronized {
				cells.getOrElse(key, {
					val new_cell = CacheCell[T](expire)(generator(key))
					cells = cells.updated(key, new_cell)
					new_cell
				})
			}
		}).value
	}

	// Force expiration of a given cell
	def clear(key: K): Unit = for (cell <- cells.get(key)) cell.clear()

	// Collect undefined cell to free memory
	// Called by the collector
	def collect(): Unit = this.synchronized {
		cells = cells.filter { case (key, cell) => cell.hasValue }
	}
}
