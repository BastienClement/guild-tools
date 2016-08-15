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
	}
}
