package gt

import data.Strings
import xuen.expr.PipesCollection

object Pipes extends PipesCollection {
	declare("uppercase", (value: String) => Option(value).map(_.toUpperCase).orNull)
	declare("lowercase", (value: String) => Option(value).map(_.toLowerCase).orNull)

	declare("class", Strings.className _)
	declare("race", Strings.raceName _)
	declare("rank", Strings.rankName _)
}
