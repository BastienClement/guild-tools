package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.{GtAlert, GtBox, GtTimeago}
import gt.service.NewsFeed
import util.jsannotation.js
import xuen.Component
import xuen.rx.{Rx, Var}

object DashboardNews extends Component[DashboardNews](
	selector = "dashboard-news",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, GtAlert, GtTimeago)
)

@js class DashboardNews extends GtHandler {
	val newsfeed = service(NewsFeed)
	val available = newsfeed.channel.open

	val news = Var[Seq[Unit]](Nil)
	val count = Rx { news.length }
}
