package gt

import Utils.using
import scala.collection.mutable

object MapBuilder {
	def apply[A, B](producer: (A) => Option[B]) = {
		new MapBuilder(producer)
	}
}

class MapBuilder[A, B](val producer: (A) => Option[B]) extends mutable.HashMap[A, B] {
	override def get(key: A): Option[B] = synchronized {
		super[HashMap].get(key) orElse {
			using(producer(key)) {
				case Some(value) => this(key) = value
				case None => /* nothing */
			}
		}
	}
}
