import boopickle.Default.generatePickler
import boopickle.DefaultBasic._

package object model {
	implicit val UserPickler: Pickler[User] = generatePickler
}
