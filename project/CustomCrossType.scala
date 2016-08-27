import org.scalajs.sbtplugin.cross.CrossType
import sbt._

object CustomCrossType extends CrossType {
	def projectDir(crossBase: File, projectType: String): File =
		crossBase / projectType

	def sharedSrcDir(projectBase: File, conf: String): Option[File] =
		Some(projectBase.getParentFile / "shared")
}
