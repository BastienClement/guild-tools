package utils

import akka.actor.Cancellable
import gt.Global.ExecutionContext

import scala.concurrent.duration.FiniteDuration

object LazyCell {
	def apply[T](expire: FiniteDuration)(generator: => T) = {
		new LazyCell[T](expire)(generator)
	}

	implicit def extract[T](cell: LazyCell[T]): T = cell.get
}

class LazyCell[T](expire: FiniteDuration)(generator: => T) {
	/**
	 * Keep track of cell state
	 */
	private var _defined = false
	def defined: Boolean = _defined

	/**
	 * This cell value
	 */
	private var value: T = _

	/**
	 * Expiration timeout
	 */
	private var timeout: Option[Cancellable] = None

	/**
	 * Get internal value or generate it if not available
	 */
	def get: T = if (_defined) value else gen()

	/**
	 * Explicitly set a new value for this cell
	 */
	def set(v: T): Unit = {
		value = v
	}

	/**
	 * Remove cached value
	 */
	def clear(): Unit = this.synchronized {
		_defined = false
		timeout = timeout.flatMap { c =>
			c.cancel()
			None
		}
	}

	/**
	 * Generate a new value
	 */
	private def gen(): T = this.synchronized {
		// Race-condition
		if (_defined) return value

		set(generator)
		_defined = true

		// Cancel previous expiration
		timeout.map(_.cancel())

		// Schedule expiration
		val t = scheduler.scheduleOnce(expire) {
			timeout = None
			clear()
		}

		timeout = Some(t)

		// Return the value
		value
	}

	/**
	 * Aliases
	 */
	def apply(): T = get
	def :=(v: T): Unit = set(v)
}
