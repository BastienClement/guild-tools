package utils

import scala.concurrent.duration._
import scala.language.implicitConversions

object LazyCache {
	def apply[T](ttl: FiniteDuration)(generator: => T) = {
		new LazyCache[T](ttl)(generator)
	}

	implicit def extract[T](cell: LazyCache[T]): T = cell.value
}

class LazyCache[T] private(ttl: FiniteDuration)(generator: => T) {
	/**
	 * This cell value
	 */
	private var _value: T = _

	/**
	 * Cell expiration
	 */
	private var expiration: Deadline = Deadline.now

	/**
	 * Get internal value or generate it if not available
	 */
	def value: T = if (hasValue) _value else gen()

	/**
	 * Explicitly set a new value for this cell
	 */
	def :=(v: T): Unit = {
		_value = v
	}

	/**
	* Explicitly set a new value for this cell (function version)
	*/
	def :=(f: (T) => T): Unit = {
		_value = f(_value)
	}

	/**
	 * Remove cached value
	 */
	def clear(): Unit = {
		expiration = Deadline.now
	}

	/**
	 * Check cell status
	 */
	def hasValue: Boolean = expiration.hasTimeLeft()

	/**
	 * Generate a new value
	 */
	private def gen(): T = this.synchronized {
		// Race-condition
		if (hasValue) return _value

		// Set the value
		this := generator
		expiration = ttl.fromNow

		// Return the value
		_value
	}

	/**
	 * Aliases
	 */
	def apply(): T = value
}
