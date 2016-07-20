package xuen
import scala.language.higherKinds
import xuen.rx.syntax.MonadicOps.ForEachBehavior

/**
  * Rx utilities
  */
package object rx {
	/**
	  * Implicit syntax extension for Rx values
	  */
	object syntax {
		/**
		  * Allow usage of the ! operator that extract the current value of
		  * the reactive value.
		  *
		  * Usually, the same action can be achieved using the implicit
		  * conversion from Rx[T] to T automatically available from the
		  * Rx object. Usage of this operator sould be limited in situations
		  * where implicit usage is impractical.
		  *
		  * @param rx the reactive value
		  * @tparam T the type of the reactive value
		  */
		implicit class ExplicitExtractor[T](private val rx: Rx[T]) extends AnyVal {
			/** Extracts the current value of the reactive value */
			@inline def ! : T = rx.get()
		}

		/**
		  * Allow usage of the common monadic operations and for-comprehension.
		  *
		  * Theses operations are not available by default since they can
		  * shadow the same methods from the wrapped type (and thus prevent
		  * implicit extraction).
		  *
		  * Explicitly importing this class will give these operations higher
		  * priority than the same actions on the wrapped value (via implicit
		  * extraction).
		  *
		  * @param rx the reactive value
		  * @tparam T the type of the reactive value
		  */
		implicit class MonadicOps[T](private val rx: Rx[T]) extends AnyVal {

			@inline def flatMap[U](f: T => Rx[U]): Rx[U] = Rx { f(rx).get() }

			@inline def map[U](f: T => U): Rx[U] = Rx { f(rx) }

			@inline def withFilter(p: T => Boolean): Rx[T] = filter(p)

			@inline def filter(p: T => Boolean): Rx[T] = {
				var value: Option[T] = None
				Rx {
					val current: T = rx
					if (value.isEmpty || p(current)) value = Some(current)
					value.get
				}
			}

			@inline def foreach(f: T => Unit)(implicit behavior: ForEachBehavior): behavior.Result[T] = {
				behavior.foreach(rx, f)
			}
		}

		object MonadicOps {
			trait ForEachBehavior {
				type Result[T]
				def foreach[T](rx: Rx[T], f: T => Unit): Result[T]
			}

			trait LowPrioForEachBehavior {
				implicit val Observer = new ForEachBehavior {
					type Result[T] = Obs
					def foreach[T](rx: Rx[T], f: T => Unit): Obs =  Obs { f(rx) } <~ rx
				}

				implicit val ObserverEager = new ForEachBehavior {
					type Result[T] = Obs
					def foreach[T](rx: Rx[T], f: T => Unit): Obs =  Obs { f(rx) } <<~ rx
				}
			}

			object ForEachBehavior extends LowPrioForEachBehavior {
				implicit val Extractor = new ForEachBehavior {
					type Result[T] = Unit
					def foreach[T](rx: Rx[T], f: T => Unit): Unit = f(rx)
				}
			}
		}
	}
}
