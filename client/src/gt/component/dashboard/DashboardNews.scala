package gt.component.dashboard

import gt.component.GtHandler
import gt.component.widget.{GtAlert, GtBox}
import gt.service.NewsFeedService
import model.NewsFeedData
import rx.Var
import rx.syntax.MonadicOps
import util.annotation.data
import util.jsannotation.js
import xuen.Component

object DashboardNews extends Component[DashboardNews](
	selector = "dashboard-news",
	templateUrl = "/assets/imports/views/dashboard.html",
	dependencies = Seq(GtBox, GtAlert, DashboardNewsFilter)
)

@js class DashboardNews extends GtHandler {
	val newsfeed = service(NewsFeedService)
	val available = newsfeed.channel.open

	val news = newsfeed.news
	val count = news ~ (_.size)

	def icon(item: NewsFeedData): String = {
		val base = item.source match {
			case "MMO" => "mmo"
			case "WOW" => "wow"
			case "BLUE" =>
				if (item.tags.contains("EU")) "eu"
				else "us"
		}
		s"/assets/images/feed/$base.png"
	}

	@data object sources {
		val mmo = Var(true)
		val blue = Var(true)
		val wow = Var(true)

		val foo = for {
			mmo <- mmo
			blue <- blue
			wow <- wow
		} yield (mmo, blue, wow)
	}
}
