package gt

import com.google.inject.AbstractModule

/**
  * Created by galedric on 20.03.2016.
  */
class Loader extends AbstractModule {
	override def configure(): Unit = {
		bind(classOf[GuildTools]).asEagerSingleton()
	}
}
