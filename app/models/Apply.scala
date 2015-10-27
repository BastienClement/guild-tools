package models

import java.sql.Timestamp
import models.mysql._
import reactive.ExecutionContext
import scala.util.Success
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
	def markAsRead(user: User, apply_id: Int): Unit = {
		val query = ApplyReadStates insertOrUpdate ApplyReadState(user.id, apply_id, SmartTimestamp.now)
		for (_ <- query.run) publish(UnreadUpdated(apply_id, false), u => u.id == user.id)
	}

	// Return messages in the discussion feed
	val feedForApply = Compiled((apply: Rep[Int], with_secret: Rep[Boolean]) => {
		ApplyFeed.filter(m => m.apply === apply && (!m.secret || with_secret)).sortBy(m => m.date.asc)
	})

	// Post a new message in an application
	def postMessage(sender: User, apply_id: Int, message: String, secret: Boolean = true, system: Boolean = false) = {
		val now = SmartTimestamp.now.toSQL
		val msg = ApplyFeedMessage(0, apply_id, sender.id, now, message, secret, system)

		// Attempt to send a private message, but the user is not a member
		if (secret && !sender.member)
			throw new Exception("You are not allowed to post private messages on applications")

		val query = (for {
		// Fetch the application and ensure that the user can access it
			apply <- Applys.filter(_.id === apply_id).result.head
			_ = if (canAccess(apply.user, apply.stage, sender)) () else throw new Exception("You are not allowed to access this application")

			// Insert the message in the database
			msg_id <- ApplyFeed += msg

			// Set have_posts and updated field for the application
			_ <- Applys.filter(_.id === apply_id).map(a => (a.have_posts, a.updated)).update((true, now))

			// Adjust application object fields to match what we just changed
			apply_updated = apply.copy(have_posts = true, updated = now)

			// Register the thread as read for the poster
			_ <- ApplyReadStates insertOrUpdate ApplyReadState(sender.id, apply_id, SmartTimestamp.now)
		} yield (apply_updated, msg_id)).transactionally

		// Check that the user can access the message
		// User can access a message if he can access the application that contains it and
		// is member of the message is not secret
		def canAccessMessage(owner: Int, stage: Int, user: User, secret: Boolean) = {
			canAccess(owner, stage, user) && (!secret || user.member)
		}

		DB.run(query) andThen {
			case Success((apply, msg_id)) =>
				def filter(u: User) = canAccessMessage(apply.user, apply.stage, u, secret)
				publish(ApplyUpdated(apply), filter)
				publish(MessagePosted(msg.copy(id = msg_id)), filter)
				publish(UnreadUpdated(apply.id, true), u => u.id != sender.id && filter(u))
		}
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

