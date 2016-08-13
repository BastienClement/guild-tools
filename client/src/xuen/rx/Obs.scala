package xuen.rx

/**
  * Reactive observer.
  *
  * Observers object encapsulates a callback that will be executed when
  * observed values are updated.
  */
trait Obs {
	/** The callback function */
	protected def callback(): Unit

	/** Trigger this observer callback */
	def trigger(): Unit = callback()

	/** Binds this observer to the given Rx value and returns the observer */
	def <~ (rx: Rx[_]): this.type = {
		rx ~> this
		this
	}

	/** Binds this observer eagerly to the given Rx value and returns the observer */
	def <<~ (rx: Rx[_]): this.type = {
		rx ~>> this
		this
	}
}

object Obs {
	/** Constructs a new observer that will invoke the given callback */
	def apply(handler: => Unit): Obs = new Obs {
		protected def callback(): Unit = handler
	}
}
