import java.text.SimpleDateFormat

object test {
  val SDF = new SimpleDateFormat("yyyy-MM-dd")    //> SDF  : java.text.SimpleDateFormat = java.text.SimpleDateFormat@f67a0200
  SDF.parse("2014-a10-16a")                       //> java.text.ParseException: Unparseable date: "2014-a10-16a"
                                                  //| 	at java.text.DateFormat.parse(Unknown Source)
                                                  //| 	at test$$anonfun$main$1.apply$mcV$sp(test.scala:5)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$$anonfun$$exe
                                                  //| cute$1.apply$mcV$sp(WorksheetSupport.scala:76)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.redirected(W
                                                  //| orksheetSupport.scala:65)
                                                  //| 	at org.scalaide.worksheet.runtime.library.WorksheetSupport$.$execute(Wor
                                                  //| ksheetSupport.scala:75)
                                                  //| 	at test$.main(test.scala:3)
                                                  //| 	at test.main(test.scala)
}