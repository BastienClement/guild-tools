package controllers.webtools

import controllers.WebTools
import models._
import models.mysql._
import play.api.libs.json.Json
import reactive._
import scala.concurrent.Future

trait Wishlist {
	this: WebTools.type =>

	private val wishlistBosses = Seq(
		"Assault", "Iron Reaver", "Kormrok",
		"Council", "Kilrogg", "Gorefiend",
		"Iskar", "Zakuun", "Xhul'horac",
		"Socrethar", "Velhari", "Mannoroth")

	def wishlist = UserAction.async { request =>
		if (!request.user.roster) throw Deny

		// Simple query for the database, no need for complex TableQuery objects
		val query = sql"SELECT data FROM poll_whishlist WHERE user = ${request.user.id}".as[String].head

		for {
			data <- DB.run(query) recover { case _ => "{}" }
		} yield {
			Ok(views.html.wt.wishlist.render(wishlistBosses, data, request.user))
		}
	}

	def wishlistSave = UserAction.async { request =>
		if (!request.user.roster) throw Deny
		(for {
			form <- request.body.asFormUrlEncoded
			data <- form.get("data")
		} yield {
			data.head
		}) match {
			case None => Future.successful(BadRequest("NOK"))
			case Some(data) =>
				// Parse the input to detect bogus data
				Json.parse(data)

				// Insert data into database
				val query = sqlu"INSERT INTO poll_whishlist SET user = ${request.user.id}, data = $data ON DUPLICATE KEY UPDATE data = VALUES(data)"
				DB.run(query) map { _ => Ok("OK") } recover { case e => println(e); throw e }
		}
	}
}
