import gtp3.{TooManyBuffersException, BufferPool}
import org.scalatestplus.play.PlaySpec

class Gtp3Spec extends PlaySpec {
	def noop[T](foo: T) = ()

	"A BufferPool" must {
		"allocate buffers of the correct size" in {
			new BufferPool().withBuffer { buf =>
				buf mustBe a [BufferPool#Buffer]
				buf.length mustBe BufferPool.defaultSize
			}
		}
		"reuse buffers" in {
			val p = new BufferPool
			var first: Array[Byte] = null
			p.withBuffer { buf =>
				first = buf
				p.count mustBe 1
				p.available mustBe 0
			}
			p.count mustBe 1
			p.available mustBe 1
			p.withBuffer { buf =>
				buf mustBe theSameInstanceAs (first)
				p.withBuffer(noop)
			}
			p.count mustBe 2
		}
		"enforce Buffer count limit" in {
			val p = new BufferPool
			def test(i: Int): Unit = p.withBuffer { b =>
				if (i == p.limit)
					intercept[TooManyBuffersException] { p.withBuffer(noop) }
				else
					test(i + 1)
			}
			test(1)
		}
		"allow to customize buffer size" in {
			new BufferPool(size = 1024).withBuffer { buf =>
				buf.length mustBe 1024
			}
		}
	}
}
