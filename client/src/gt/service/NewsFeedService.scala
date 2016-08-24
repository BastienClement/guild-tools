package gt.service

import boopickle.DefaultBasic._
import gt.service.base.{Delegate, Service}
import model.NewsFeedData
import rx.Var

object NewsFeedService extends Service with Delegate {
	val channel = registerChannel("newsfeed", lzy = false)
	val news = Var(Seq.empty[NewsFeedData])

	message("update") { (data: Seq[NewsFeedData]) => news := data }
}
