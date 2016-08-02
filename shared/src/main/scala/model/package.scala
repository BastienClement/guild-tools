import boopickle.DefaultBasic._

package object model {
	implicit val ToonPickler: Pickler[Toon] = PicklerGenerator.generatePickler[Toon]
	implicit val UserPickler: Pickler[User] = PicklerGenerator.generatePickler[User]
}
