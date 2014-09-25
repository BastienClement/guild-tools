package gt

import scala.collection.mutable

object MapBuilder {
	def apply[A, B](producer: (A) => Option[B]) = {
		new MapBuilder(producer)
	}
}

class MapBuilder[A, B](val producer: (A) => Option[B]) extends mutable.HashMap[A, B] {
	override def get(key: A): Option[B] = this.synchronized {
		super[HashMap].get(key) orElse {
			val value = producer(key)
			if (value.isDefined) this(key) = value.get
			value
		}
	}
}
