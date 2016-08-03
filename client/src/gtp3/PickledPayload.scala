package gtp3

import boopickle.DefaultBasic._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import util.buffer.PolymorphicBuffer

class PickledPayload(val buffer: PolymorphicBuffer) extends AnyVal {
	@inline def as[T: Pickler]: T = Unpickle[T].fromBytes(buffer.toByteBuffer)
}

object PickledPayload {
	implicit class FutureUnpickle(private val future: Future[PickledPayload]) extends AnyVal {
		@inline def as[T: Pickler]: Future[T] = future.map(_.as[T])
		@inline def apply[T1: Pickler, R](fn: T1 => R): Future[R] = future.map { pp => fn(pp.as[T1]) }
		@inline def apply[T1: Pickler, T2: Pickler, R](fn: (T1, T2) => R): Future[R] = apply(fn.tupled)
		@inline def apply[T1: Pickler, T2: Pickler, T3: Pickler, R](fn: (T1, T2, T3) => R): Future[R] = apply(fn.tupled)
		@inline def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, R](fn: (T1, T2, T3, T4) => R): Future[R] = apply(fn.tupled)
		@inline def apply[T1: Pickler, T2: Pickler, T3: Pickler, T4: Pickler, T5: Pickler, R](fn: (T1, T2, T3, T4, T5) => R): Future[R] = apply(fn.tupled)
	}
}
