package gt

import com.google.inject.AbstractModule

class Loader extends AbstractModule {
	override def configure(): Unit = {
		println("Configuring")
		bind(classOf[GuildTools]).asEagerSingleton()
	}
}
