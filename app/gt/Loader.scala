package gt

import com.google.inject.AbstractModule

class Loader extends AbstractModule {
	override def configure(): Unit = {
		bind(classOf[GuildTools]).asEagerSingleton()
	}
}
