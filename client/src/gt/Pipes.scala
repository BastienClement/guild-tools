package gt

import data.Strings
import xuen.expr.PipesCollection

object Pipes extends PipesCollection {
	declare("uppercase", (value: String) => value.toUpperCase)
	declare("lowercase", (value: String) => value.toLowerCase)

	declare("class", Strings.className _)
	declare("race", Strings.raceName _)
	declare("rank", Strings.rankName _)
}
