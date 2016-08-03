import boopickle.DefaultBasic._

package object model {
	implicit val ToonPickler = PicklerGenerator.generatePickler[Toon]
	implicit val UserPickler = PicklerGenerator.generatePickler[User]
}
