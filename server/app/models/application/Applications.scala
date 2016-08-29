package models.application

import models.User
import models.application.ApplicationEvents.ApplyUpdated
import reactive.ExecutionContext
import scala.util.Success
import utils.DateTime
import utils.SlickAPI._

case class Application(id: Int, user: Int, date: DateTime, stage: Int, have_posts: Boolean, updated: DateTime)

object Applications extends TableQuery(new Applications(_)) {
	/** Filter for applications that a given user can access */
	def canAccess(user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean])(application: Applications): Rep[Boolean] = {
		val stage = application.stage
		val valid_officer_access = (stage > Stage.Trial.id || stage === Stage.Pending.id) && promoted
		val valid_member_access = (stage > Stage.Pending.id && stage <= Stage.Trial.id) && (application.user === user || member)
		valid_officer_access || valid_member_access
	}

	/** Check if a user can access a given application */
	def canAccess(user: User, application: Application) = {
		if (application.stage > Stage.Trial.id || application.stage == Stage.Pending.id) user.promoted
		else if (application.stage > Stage.Pending.id && application.stage <= Stage.Trial.id) application.user == user.id || user.member
		else false
	}

	/** Fetch every open applications visible by the user and their unread status */
	val listOpen = Compiled((user_id: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		for {
			application <- Applications.sortBy(_.updated.desc).filter(canAccess(user_id, member, promoted))
			if application.stage <= Stage.Trial.id
			unread = ApplicationReadStates.isUnread.extract(application.id, user_id, member)
		} yield (application, unread)
	})

	/** Fetch every open applications visible by the user and their unread status */
	def listOpenForUser(user: User) = listOpen(user.id, user.member, user.promoted)

	/** Fetch application by id */
	val byId = Compiled((id: Rep[Int]) => {
		for (a <- Applications if a.id === id) yield a
	})

	/** Fetch application by id and check that the user can access it */
	val byIdChecked = Compiled((id: Rep[Int], user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		byId.extract(id).filter(canAccess(user, member, promoted))
	})

	/** Fetch application body data by id */
	val data = Compiled((id: Rep[Int]) => {
		byId.extract(id).map(a => (a.data_type, a.data))
	})

	/** Fetch application body data by id and check that the user can access it */
	val dataChecked = Compiled((id: Rep[Int], user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		byIdChecked.extract(id, user, member, promoted).map(a => (a.data_type, a.data))
	})

	/** Fetch last application for a user */
	val lastForUser = Compiled((user: Rep[Int]) => {
		Applications.sortBy(_.id.desc).filter(_.user === user).take(1)
	})

	/**
	  * Create a new application
	  */
	def create(user: User, data_type: DataType, data: String) = {
		// Current time
		val now = DateTime.now

		// The default application stage
		val stage = Stage.Pending.id

		// INSERT query
		val mapping = Applications.map(a => (a.user, a.date, a.data_type, a.data, a.stage, a.have_posts, a.updated))
		val insert = (mapping returning Applications.map(_.id)) += (user.id, now, data_type, data, stage, false, now)

		// Create application then post created message in it
		val query = for {
			id <- insert
			(application, _) <- ApplicationFeed.postMessage(user, id, "created the application", false, true)
		} yield application

		// Execute action, then broadcast ApplyUpdated event
		query.run andThen {
			case Success(application) =>
				ApplicationEvents.publish(ApplyUpdated(application), u => canAccess(u, application))
		}
	}

	/**
	  * Changes an application stage.
	  */
	def changeStage(id: Int, user: User, stage: Stage) = {
		val query = for {
			old_apply <- Applications.filter(_.id === id).result.head
			_ = if (old_apply.stage == stage.id) throw new Exception("Application stage unchanged")
			_ <- Applications.filter(_.id === id).map(_.stage).update(stage.id)
			old_stage_name = Stage.fromId(old_apply.stage).name
			new_stage_name = stage.name
			_ <- ApplicationFeed.postMessage(user, id, s"changed the application stage from **$old_stage_name** to **$new_stage_name**", false, true)
			new_apply <- Applications.filter(_.id === id).result.head
		} yield new_apply

		query.transactionally.run andThen {
			case Success(application) =>
				ApplicationEvents.publish(ApplyUpdated(application), u => canAccess(u, application))
		}
	}

	/*
	def changeState(user: User, application: Int, stage: Int) = {
		if (stage < PENDING || stage > ARCHIVED)
			throw new IllegalArgumentException("Invalid stage")

		if (!user.promoted)
			throw new IllegalAccessException("Unpromoted user cannot change application state")

		val query = for {
			// Fetch the current application and ensure that it is not already in the requested stage
			apply <- Applications.filter(_.id === application).result.head
			_ = if (apply.stage == stage) throw new IllegalStateException("Application is already in the given stage")

			// Update the application
			_ <- Applications.filter(_.id === application).map(_.stage).update(stage)

			// Send the update message in the feed
			update_message = s"${user.name} changed the application stage from ${stageName(apply.stage)} to ${stageName(stage)}"
			_ <- postMessage(user, application, update_message, false, true)

			// Remove any read state relative to the application
			_ <- ApplicationReadStates.filter(_.apply === apply.id).delete
		} yield apply

		query
	}*/
}

class Applications(tag: Tag) extends Table[Application](tag, "gt_apply") {
	def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
	def user = column[Int]("user")
	def date = column[DateTime]("date")
	def stage = column[Int]("stage")
	def data_type = column[DataType]("data_type")
	def data = column[String]("data")
	def have_posts = column[Boolean]("have_posts")
	def updated = column[DateTime]("updated")

	def * = (id, user, date, stage, have_posts, updated) <> (Application.tupled, Application.unapply)
}
