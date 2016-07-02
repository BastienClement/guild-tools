import org.scalatestplus.play.PlaySpec
import utils.BiMap

class UtilsSpec extends PlaySpec{
	"A BiMap" must {
		object Singleton
		val ref = Map("a" -> 'A, "b" -> Singleton, "c" -> 42)
		"be initially empty" in {
			BiMap.empty.size mustBe 0
		}
		"allow to store by key and retrive by key" in {
			val bimap = BiMap.empty[String, Any]
			for ((k, v) <- ref) bimap.put(k -> v)
			for ((k, v) <- ref) bimap.get(k) mustBe Some(v)
		}
		"allow to store by key and retrive by value" in {
			val bimap = BiMap.empty[String, Any]
			for ((k, v) <- ref) bimap.put(k -> v)
			for ((k, v) <- ref) bimap.getKey(v) mustBe Some(k)
		}
		"not allow duplicate key" in {
			val bimap = BiMap.empty[String, Number]
			bimap.put("a" -> 1)
			bimap.put("a" -> 2)
			bimap.get("a") mustBe Some(2)
			bimap.getKey(1) mustBe None
		}
		"not allow duplicate value" in {
			val bimap = BiMap.empty[String, Number]
			bimap.put("a" -> 1)
			bimap.put("b" -> 1)
			bimap.getKey(1) mustBe Some("b")
			bimap.get("a") mustBe None
		}
		"allow removal of key or value" in {
			val bimap = BiMap.empty[String, Any]
			for ((k, v) <- ref) bimap.put(k -> v)
			bimap.remove("a")
			bimap.get("a") mustBe None
			bimap.getKey('A) mustBe None
			bimap.removeValue(Singleton)
			bimap.get("b") mustBe None
			bimap.getKey(Singleton) mustBe None
		}
		"allow unrelated mapping" in {
			val bimap = BiMap.unrelated[String, Int]
			bimap.put("foo" -> 2)
			bimap.put("bar" -> 3)
			bimap.get("foo") mustBe Some(2)
			bimap.get(2) mustBe Some("foo")
			bimap.remove("foo")
			bimap.get(2) mustBe None
			bimap.remove(3)
			bimap.get(3) mustBe None
		}
	}
}
