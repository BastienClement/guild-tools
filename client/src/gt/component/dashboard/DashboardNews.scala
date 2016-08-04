package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.{GtAlert, GtBox, GtTimeago}
import gt.service.NewsFeed
import util.annotation.data
import util.jsannotation.js
import xuen.Component
import xuen.rx.syntax.MonadicOps
import xuen.rx.{Rx, Var}

object DashboardNews extends Component[DashboardNews](
	selector = "dashboard-news",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, GtAlert, GtTimeago, DashboardNewsFilter)
)

@js class DashboardNews extends GtHandler {
	val newsfeed = service(NewsFeed)
	val available = newsfeed.channel.open

	val news = Var[Seq[Unit]](Nil)
	val count = Rx { news.length }

	@data object sources {
		val mmo = Var(true)
		val blue = Var(true)
		val wow = Var(true)

		val foo = for {
			mmo <- mmo
			blue <- blue
			wow <- wow
		} yield (mmo, blue, wow)

		foo ~> { v => println(v) }
	}
}
