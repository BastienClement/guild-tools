import com.google.inject.AbstractModule
import utils.SlickAPI

class Module extends AbstractModule {
	override def configure() = {
		requestInjection(SlickAPI)
	}
}
