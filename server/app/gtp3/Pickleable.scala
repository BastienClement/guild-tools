package gtp3

import boopickle.DefaultBasic._
import java.nio.ByteBuffer
import java.util.zip.Deflater
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scodec.bits.ByteVector
import slick.dbio.{DBIOAction, NoStream}
import utils.SlickAPI._

trait Pickleable[-T] {
	def pickle(data: T): Future[ByteBuffer]
	@inline final def picklePayload(data: T): Future[Payload] = pickle(data).map(Pickleable.maybeDeflate)
}

object Pickleable {
	private def deflate(buf: ByteBuffer): Payload = pool.withBuffer { output =>
		val data = new Array[Byte](buf.remaining())
		buf.get(data)

		val deflater = new Deflater()
		deflater.setInput(data)
		deflater.finish()

		val length = deflater.deflate(output)
		assert(deflater.finished())

		new Payload(ByteVector(output.slice(0, length)), PayloadFlags.Compress | PayloadFlags.PickledData)
	}

	private def maybeDeflate(buf: ByteBuffer): Payload = {
		if (buf.remaining() > Protocol.CompressLimit) deflate(buf)
		else new Payload(ByteVector.view(buf), PayloadFlags.PickledData)
	}

	implicit def PicklerIsPickleable[T: Pickler]: Pickleable[T] = new Pickleable[T] {
		def pickle(data: T): Future[ByteBuffer] = Future.successful(Pickle.intoBytes(data))
	}

	implicit def FutureIsPickleable[T: Pickleable]: Pickleable[Future[T]] = new Pickleable[Future[T]] {
		def pickle(data: Future[T]): Future[ByteBuffer] = data.flatMap(implicitly[Pickleable[T]].pickle)
	}

	type DBIOA[T] = DBIOAction[T, NoStream, Nothing]
	implicit def DBIOActionIsPickleable[T: Pickleable]: Pickleable[DBIOA[T]] = new Pickleable[DBIOA[T]] {
		def pickle(action: DBIOA[T]): Future[ByteBuffer] = action.run.flatMap(implicitly[Pickleable[T]].pickle)
	}
}
