import scala.util.Random
import java.security.SecureRandom
import java.math.BigInteger
import play.api.libs.json.Json

abstract class AFooBar(val title: String)
case class EnglishName(name: Seq[String])
case class FooBar(name: EnglishName, surname: EnglishName) extends AFooBar("Mr.")

object Test {
	val foo = FooBar(EnglishName(Seq("Bastien", "Michel", "Tibault")), EnglishName(Seq("Clément")))
                                                  //> foo  : FooBar = FooBar(EnglishName(List(Bastien, Michel, Tibault)),EnglishNa
                                                  //| me(List(ClÃ©ment)))
	implicit val EN = Json.format[EnglishName]//> EN  : play.api.libs.json.OFormat[EnglishName] = play.api.libs.json.OFormat$$
                                                  //| anon$1@4b1c1ea0
	implicit val FB = Json.format[FooBar]     //> FB  : play.api.libs.json.OFormat[FooBar] = play.api.libs.json.OFormat$$anon$
                                                  //| 1@2bbf4b8b
	Json.toJson(foo)                          //> res0: play.api.libs.json.JsValue = {"name":{"name":["Bastien","Michel","Tiba
                                                  //| ult"]},"surname":{"name":["ClÃ©ment"]}}
}