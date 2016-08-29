package controllers.webtools

import controllers.webtools.WtController.Deny
import play.api.libs.json.{JsNull, Json}
import play.api.mvc.Controller
import reactive._
import scala.concurrent.Future
import utils.SlickAPI._

class WishlistController extends Controller with WtController {
	/**
	  * List of bosses in the wishlist
	  */
	private val wishlistBosses = Seq(
		"Assault", "Iron Reaver", "Kormrok",
		"Council", "Kilrogg", "Gorefiend",
		"Iskar", "Zakuun", "Xhul'horac",
		"Socrethar", "Velhari", "Mannoroth")

	/**
	  * Own wishlist form
	  */
	def wishlist = UserAction.async { req =>
		if (!req.user.roster) throw Deny

		// Simple query for the database, no need for complex TableQuery objects
		val query = sql"SELECT data FROM poll_whishlist WHERE user = ${req.user.id}".as[String].head

		for {
			data <- DB.run(query) recover { case _ => "{}" }
		} yield {
			Ok(views.html.wt.wishlist.render(wishlistBosses, data, req))
		}
	}

	/**
	  * Save the user wishlist
	  */
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
				DB.run(query) map { _ => Ok("OK") } recover { case _ => BadRequest("NOK") }
		}
	}

	/**
	  * Display every players wishes for a specific boss.
	  */
	def wishlistBoss(boss: String) = UserAction.async { req =>
		if (!req.user.roster) throw Deny
		if (!wishlistBosses.contains(boss)) throw Deny

		val query = sql"""
			SELECT c.name, c.class, w.data
			FROM phpbb_users AS u
			JOIN gt_chars AS c ON u.user_id = c.owner AND c.main = 1
			LEFT JOIN poll_whishlist AS w ON w.user = u.user_id
			WHERE u.group_id IN (9, 11, 8)
		""".as[(String, Int, Option[String])]

		for {
			rows <- DB.run(query) recover { case _ => Vector() }
		} yield {
			val data = rows.map { case (u, c, d) =>
				val boss_data = d.map(Json.parse).getOrElse(JsNull) \ boss
				Json.arr(u, c, boss_data.getOrElse(JsNull))
			}
			val json = Json.stringify(Json.toJson(data))
			Ok(views.html.wt.wishlist_boss.render(boss, json, req))
		}
	}
}
