import { Component, Injector } from "utils/di";
import { Deferred } from "utils/deferred";
import { Server } from "client/server";
import { ResourceLoader } from "client/resource-loader";

/**
 * The global reference to the Application instance
 */
declare var GuildTools: Application;

/**
 * GuildTools
 */
@Component
export class Application {
    constructor(
        public server: Server,
		public loader: ResourceLoader,
		public injector: Injector)
	{
		// Expose this object in the global scope
		GuildTools = this;
	}
	
	/**
	 * Initialize the GuildTools application
	 */
	main(): void {
		const loading_delay = Deferred.delay(1000);
		const socket_endpoint = this.loader.fetch("/api/socket_url")
		
		const init_pipeline = Deferred.pipeline(socket_endpoint, [
			(url: string) => this.server.connect(url),
			() => new Authenticator(this).begin(),
			() => loading_delay
		]);
		
		init_pipeline.then(() => {
			console.log("Loading done");
		}, (e) => {
			console.error("Loading failed", e);
		});
	}
}

/**
 * Authentication procedure helper
 */
class Authenticator {
	constructor(
		public app: Application) { }
	
	/**
	 * Begin the authentication process
	 */
	begin(): Promise<void> {
		return null;
	}
}
