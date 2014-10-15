import java.text.SimpleDateFormat

object test {
 	implicit def s2i(s: String): Int = 2      //> s2i: (s: String)Int
 	implicit def i2d(i: Double): Double = i * 2
                                                  //> i2d: (i: Double)Double
 	implicit def s2d(s: String): Double = { val a: Int = s; s }
                                                  //> s2d: (s: String)Double
 	val a = "Hello"                           //> a  : String = Hello/
 	Math.pow(a, 2)
}