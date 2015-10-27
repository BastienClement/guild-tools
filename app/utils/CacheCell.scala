package utils

import reactive.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.util.Failure

object CacheCell {
	// Construct a cache cell with a given ttl and generator
	def apply[T](ttl: FiniteDuration)(generator: => T) = new CacheCell[T](ttl)(generator)

	// Async cache cell
	def async[T](ttl: FiniteDuration)(generator: => Future[T]) = {
		lazy val cell: CacheCell[Future[T]] = CacheCell(ttl) {
			generator andThen {
				case Failure(_) => cell.clear()
			}
		}
		cell
	}

	// Implicit extractor converting a CacheCell[T] to a T
	object Implicit {
		implicit def extractor[T](cell: CacheCell[T]): T = cell.value
	}
}

class CacheCell[T] private(ttl: FiniteDuration)(generator: => T) {
	// This cell value
	@volatile private[this] var _value: Option[T] = None

	// Cell expiration deadline
	@volatile private[this] var expiration: Deadline = Deadline.now

	// Get internal value or generate it if not available
	def value: T = if (hasValue) _value.get else gen()

	// Explicitly set a new value for this cell
	def :=(v: T): Unit = {
		_value = Some(v)
	}

	// Explicitly set a new value for this cell
	// This version take a function to compute the new value from the old one
	// Example: foo := (_ + 1)
	def :=(f: (T) => T): Unit = this.synchronized {
		_value = Some(f(_value.get))
	}

	// Remove cached value
	def clear(): Unit = {
		_value = None
		expiration = Deadline.now
	}

	// Check cell status
	def hasValue: Boolean = _value.isDefined && expiration.hasTimeLeft()

	// Generate a new value
	private[this] def gen(): T = this.synchronized {
		// Race-condition
		// Value was generated while waiting for synchronization
		if (hasValue) return _value.get

		// Set the value
		this := generator
		expiration = ttl.fromNow

		// Return the value
		_value.get
	}

	// Refresh this object value
	def refresh(): T = {
		clear()
		gen()
	}

	// Aliases
	def apply(): T = value
}
