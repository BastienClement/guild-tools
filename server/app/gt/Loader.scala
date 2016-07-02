package gt

import com.google.inject.AbstractModule

class Loader extends AbstractModule {
	override def configure(): Unit = {
		requestInjection(GuildTools)
	}
}
