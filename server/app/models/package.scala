import boopickle.DefaultBasic._
import models.application.{Application, ApplicationMessage}

package object models {
	implicit val applyJsonFormat = PicklerGenerator.generatePickler[Application]
	implicit val applyFeedMessageJsonFormat = PicklerGenerator.generatePickler[ApplicationMessage]
	implicit val chatMessageFormat = PicklerGenerator.generatePickler[ChatMessage]
	implicit val chatWhisperFormat = PicklerGenerator.generatePickler[ChatWhisper]
	implicit val composerLockoutFormat = PicklerGenerator.generatePickler[ComposerLockout]
	implicit val composerGroupFormat = PicklerGenerator.generatePickler[ComposerGroup]
	implicit val composerSlotFormat = PicklerGenerator.generatePickler[ComposerSlot]
	implicit val streamJsonFormat = PicklerGenerator.generatePickler[live.Stream]
}
