import play.api.{Application, GlobalSettings}

object Global extends GlobalSettings {
	override def onStart(app: Application) = {
		//Akka.system().actorOf(Props(new SessionManager), "session")
	}
}
