package models

import java.sql.Timestamp
import models.mysql._
import reactive.ExecutionContext
import utils.{PubSub, SmartTimestamp}

// ============================================================================

case class Apply(id: Int, user: Int, date: Timestamp, stage: Int, have_posts: Boolean, updated: Timestamp) {
	require(stage >= Applys.PENDING && stage <= Applys.ARCHIVED)
}

object Applys extends TableQuery(new Applys(_)) with PubSub[User] {
	// Stages
	final val PENDING = 0
	final val REVIEW = 1
	final val TRIAL = 2
	final val REFUSED = 3
	final val ACCEPTED = 4
	final val ARCHIVED = 5

	// Stage id -> names
	def stageName(stage: Int) = stage match {
		case PENDING => "Pending"
		case REVIEW => "Review"
		case TRIAL => "Trial"
		case REFUSED => "Refused"
		case ACCEPTED => "Accepted"
		case ARCHIVED => "Archived"
		case _ => "Unknown"
	}

	// Events
	case class UnreadUpdated(apply: Int, unread: Boolean)
	case class ApplyUpdated(apply: Apply)
	case class MessagePosted(message: ApplyFeedMessage)

	// Check that the current user can access a specific apply
	def canAccess(owner: Int, stage: Int, user: User): Boolean = {
		// Access to an archived or pending apply require promoted
		if (stage > Applys.TRIAL || stage == Applys.PENDING) user.promoted
		// Access to an open (not pending) apply require member or own apply
		else if (stage > Applys.PENDING) user.member || owner == user.id
		// Other use cases are undefined thus not allowed
		else false
	}

	// Fetch every open applications visible by the user and their unread status
	def openForUser(user: User) = openForUserQuery(user.id, user.member, user.promoted)
	private val openForUserQuery = Compiled((user: Rep[Int], member: Rep[Boolean], promoted: Rep[Boolean]) => {
		val default_read = SmartTimestamp(2000, 1, 1).toSQL
		for {
			apply <- Applys.sortBy(_.updated.desc) if apply.stage < Applys.REFUSED && (member || apply.user === user) && (apply.stage > Applys.PENDING || promoted)
			last_msg = ApplyFeed.filter(m => m.apply === apply.id && (member || !m.secret)).map(_.date).max.getOrElse(default_read)
			read = ApplyReadStates.filter(r => r.apply === apply.id && r.user === user).map(_.date).max.getOrElse(default_read)
		} yield (apply, read < last_msg)
	})

	// Update the read state flag
	def markAsRead(user: User, apply_id: Int) = {
		for {
			r <- ApplyReadStates insertOrUpdate ApplyReadState(user.id, apply_id, SmartTimestamp.now)
			_ = publish(UnreadUpdated(apply_id, false), u => u.id == user.id)
		} yield ()
	}

	// Return messages in the discussion feed
	val feedForApply = Compiled((apply: Rep[Int], with_secret: Rep[Boolean]) => {
		ApplyFeed.filter(m => m.apply === apply && (!m.secret || with_secret)).sortBy(m => m.date.asc)
	})

	// Post a new message in an application
	def postMessage(sender: User, apply_id: Int, message: String, secret: Boolean = true, system: Boolean = false) = {
		// Attempt to send a private message, but the user is not a member
		if (secret && !sender.member)
			throw new Exception("You are not allowed to post private messages on applications")

		val now = SmartTimestamp.now.toSQL
		val msg = ApplyFeedMessage(0, apply_id, sender.id, now, message, secret, system)

		val post_message_query = for {
			// Fetch the application and ensure that the user can access it
			apply <- Applys.filter(_.id === apply_id).result.head
			_ = if (!canAccess(apply.user, apply.stage, sender)) throw new Exception("You are not allowed to access this application")

			// Insert the message in the database
			msg_id <- (ApplyFeed returning ApplyFeed.map(_.id)) += msg

			// Set have_posts and updated field for the application
			_ <- Applys.filter(_.id === apply_id).map(a => (a.have_posts, a.updated)).update((true, now))

			// Adjust application object fields to match what we just changed
			apply_updated = apply.copy(have_posts = true, updated = now)
		} yield (apply_updated, msg_id)

		for {
			(apply, msg_id) <- post_message_query.transactionally
			_ = {
				// Only send event to users that can access application and are members if the message is secret
				def filter(user: User) = canAccess(apply.user, apply.stage, user) && (!secret || user.member)
				publish(ApplyUpdated(apply), filter)
				publish(MessagePosted(msg.copy(id = msg_id)), filter)
				publish(UnreadUpdated(apply.id, true), filter)
			}
		} yield (apply, msg_id)
	}

	def changeState(user: User, application: Int, stage: Int) = {
		if (stage < PENDING || stage > ARCHIVED)
			throw new IllegalArgumentException("Invalid stage")

		if (!user.promoted)
			throw new IllegalAccessException("Unpromoted user cannot change application state")

		val query = for {
			// Fetch the current application and ensure that it is not already in the requested stage
			apply <- Applys.filter(_.id === application).result.head
			_ = if (apply.stage == stage) throw new IllegalStateException("Application is already in the given stage")

			// Update the application
			_ <- Applys.filter(_.id === application).map(_.stage).update(stage)

			// Send the update message in the feed
			update_message = s"${user.name} changed the application stage from ${stageName(apply.stage)} to ${stageName(stage)}"
			_ <- postMessage(user, application, update_message, false, true)

			// Remove any read state relative to the application
			_ <- ApplyReadStates.filter(_.apply === apply.id).delete
		} yield apply

		query
	}
}

class Applys(tag: Tag) extends Table[Apply](tag, "gt_apply") {
	def id = column[Int]("id", O.PrimaryKey)
	def user = column[Int]("user")
	def date = column[Timestamp]("date")
	def stage = column[Int]("stage")
	def data = column[String]("data")
	def have_posts = column[Boolean]("have_posts")
	def updated = column[Timestamp]("updated")

	def * = (id, user, date, stage, have_posts, updated) <>(Apply.tupled, Apply.unapply)
}

// ============================================================================

case class ApplyFeedMessage(id: Int, apply: Int, user: Int, date: Timestamp, text: String, secret: Boolean, system: Boolean)

object ApplyFeed extends TableQuery(new ApplyFeed(_))

class ApplyFeed(tag: Tag) extends Table[ApplyFeedMessage](tag, "gt_apply_feed") {
	def id = column[Int]("id", O.PrimaryKey)
	def apply = column[Int]("apply")
	def user = column[Int]("user")
	def date = column[Timestamp]("date")
	def text = column[String]("text")
	def secret = column[Boolean]("secret")
	def system = column[Boolean]("system")

	def * = (id, apply, user, date, text, secret, system) <>(ApplyFeedMessage.tupled, ApplyFeedMessage.unapply)
}

// ============================================================================

case class ApplyReadState(user: Int, apply: Int, date: Timestamp)

object ApplyReadStates extends TableQuery(new ApplyReadStates(_))

class ApplyReadStates(tag: Tag) extends Table[ApplyReadState](tag, "gt_apply_read") {
	def user = column[Int]("user", O.PrimaryKey)
	def apply = column[Int]("apply", O.PrimaryKey)
	def date = column[Timestamp]("date")

	def * = (user, apply, date) <>(ApplyReadState.tupled, ApplyReadState.unapply)
}

