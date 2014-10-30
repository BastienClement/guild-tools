package utils

import scala.concurrent.duration._

object LazyCell {
	def apply[T](ttl: FiniteDuration)(generator: => T) = {
		new LazyCell[T](ttl)(generator)
	}

	implicit def extract[T](cell: LazyCell[T]): T = cell.get
}

class LazyCell[T] private(ttl: FiniteDuration)(generator: => T) {
	/**
	 * This cell value
	 */
	private var value: T = _

	/**
	 * Cell expiration
	 */
	private var expiration: Deadline = Deadline.now

	/**
	 * Get internal value or generate it if not available
	 */
	def get: T = if (expiration.hasTimeLeft()) value else gen()

	/**
	 * Explicitly set a new value for this cell
	 */
	def set(v: T): Unit = {
		value = v
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
	def isExpired: Boolean = expiration.isOverdue()

	/**
	 * Generate a new value
	 */
	private def gen(): T = this.synchronized {
		// Race-condition
		if (expiration.hasTimeLeft()) return value

		// Set the value
		set(generator)
		expiration = ttl.fromNow

		// Return the value
		value
	}

	/**
	 * Aliases
	 */
	def apply(): T = get
	def :=(v: T): Unit = set(v)
}
