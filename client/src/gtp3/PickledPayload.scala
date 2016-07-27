package gtp3

import boopickle.Default.Unpickle
import boopickle.Pickler
import java.nio.ByteBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class PickledPayload(val buffer: ByteBuffer) extends AnyVal {
	@inline def as[T: Pickler]: T = Unpickle[T].fromBytes(buffer)
}

object PickledPayload {
	implicit class FutureUnpickle(private val future: Future[PickledPayload]) extends AnyVal {
		@inline def as[T: Pickler]: Future[T] = future.map(_.as[T])
	}
}
