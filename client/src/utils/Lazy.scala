package utils

import scala.language.implicitConversions

class Lazy[T](compute: => T) {
	private[this] var computed: Boolean = false
	private[this] var value: T  = _
	@inline final private[Lazy] def get(): T = {
		if (computed) value
		else {
			value = compute
			computed = true
			value
		}
	}
}

object Lazy {
	@inline final def apply[T](compute: => T): Lazy[T] = new Lazy[T](compute)
	@inline final implicit def extractor[T](lzy: Lazy[T]): T = lzy.get()
}
