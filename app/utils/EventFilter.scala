package utils

import actors.{Dispatchable, EventListener}
import utils.EventFilter._

/**
 * Common event-filtering related utilities
 */
object EventFilter {
	/**
	 * Alias for filter function type
	 */
	type FilterFunction = PartialFunction[Dispatchable, Boolean]

	/**
	 * Handle storing an optional unbind handler
	 */
	class CleanupHelper() {
		/**
		 * The registered cleanup function
		 */
		var handler: Option[() => Unit] = None

		/**
		 * Bind a new function as cleanup handler
		 */
		def onUnbind(fn: => Unit): Unit = {
			handler = Some(() => fn)
		}

		/**
		 * Call the cleanup function and rester the helper to blank state
		 */
		def apply(): Unit = {
			for (fn <- handler) fn()
			handler = None
		}
	}

	/**
	 * Default filter, returning false for any event
	 */
	private val FilterNone: FilterFunction = {
		case _ => false
	}
}

/**
 * Implement event filtering behavior
 */
trait EventFilter extends EventListener {
	/**
	 * The current filter function
	 */
	private var filter: FilterFunction = FilterNone

	/**
	 * The current event being dispatched, allow modification via the !< method
	 */
	private var event: Dispatchable = null

	/**
	 * Final list of events to dispatch, allows the !~ method
	 */
	private var events: List[Dispatchable] = Nil

	/**
	 * The cleaner helper for this EventFilter instance
	 */
	private val cleanup: CleanupHelper = new CleanupHelper()

	/**
	 * Remove any registered event filter function
	 */
	def unbindEvents(): Unit = {
		filter = FilterNone
		cleanup()
	}

	/**
	 * Define a new event filter function
	 */
	def bindEvents(new_filter: FilterFunction) = {
		unbindEvents()
		filter = new_filter
		cleanup
	}

	/**
	 * Replace the current event with a new one
	 */
	def !< (e: Dispatchable): Boolean = {
		event = e
		true
	}

	/**
	 * Generate an additionnal event in response to another event
	 */
	def !~ (e: Dispatchable): Boolean = {
		events ::= e
		true
	}

	/**
	 * Handle unfiltered event reception
	 */
	def onEvent(e: Dispatchable): Unit = {
		// Reset dispatcher state
		event = e
		events = Nil

		// Apply the filter function and then add the event to the final list
		if (filter.applyOrElse(e, FilterNone)) events ::= event

		// Dispatch every generated events
		for (event <- events) onEventFiltered(event)
	}

	/**
	 * Must be implement to handle filtered events
	 */
	def onEventFiltered(event: Dispatchable)
}
