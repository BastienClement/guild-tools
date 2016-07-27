package gtp3

import boopickle.DefaultBasic._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.typedarray.ArrayBuffer
import util.GtWorker
import util.buffer.{BufferOps, PolymorphicBuffer}

private[gtp3] object Payload {
	val compressWorker = new GtWorker("/assets/workers/compress.js")

	def encode[D: Pickler](payload: D): Future[(PolymorphicBuffer, Int)] = {
		val buffer = Pickle.intoBytes(payload).toByteArray
		if (buffer.length >= Protocol.CompressLimit) {
			compressWorker.request[ArrayBuffer]("deflate", buffer.toArrayBuffer).map { deflated =>
				(deflated.polymorphic, PayloadFlags.PickledData & PayloadFlags.Compress)
			}
		} else {
			Future.successful {
				(buffer.polymorphic, PayloadFlags.PickledData)
			}
		}
	}

	def inflate(buffer: PolymorphicBuffer, flags: Int): Future[PolymorphicBuffer] = {
		if ((flags & PayloadFlags.Compress) != 0) {
			compressWorker.request[ArrayBuffer]("inflate", buffer.toArrayBuffer).map(_.polymorphic)
		} else {
			Future.successful { buffer }
		}
	}
}
