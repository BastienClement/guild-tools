package util

import scala.collection.mutable

class EventSource[T] {
	type Listener = T => Unit

	private val listeners = mutable.Set.empty[Listener]

	def ~> (listener: Listener) = listeners.add(listener)
	def ~!> (listener: Listener) = listeners.remove(listener)

	def emit(value: T): Unit = for (listener <- listeners) listener(value)
}

object EventSource {
	class Simple {
		type Listener = () => Unit

		private val listeners = mutable.Set.empty[Listener]

		def ~>[T] (listener: => T) = listeners.add(() => listener)
		def ~!>[T] (listener: => T) = listeners.remove(() => listener)

		def emit(): Unit = for (listener <- listeners) listener()
	}
}
