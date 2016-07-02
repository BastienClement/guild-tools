import {Injector, Constructor} from "./utils/DI";
import {Application} from "./client/Application";
import "./utils/Async";
import "./services/Providers"

/**
 * Prevents right-click context menu unless the shift key is pressed.
 */
function prevent_right_click() {
	document.addEventListener("contextmenu", (e: MouseEvent) => {
		if (!e.shiftKey) {
			e.preventDefault();
		}
	});
}

/**
 * Load and initialize Guild Tools
 * Create the default injector and use it
 * to construct the main Application object
 */
export default async function boot() {
	// Prevent context menu
	prevent_right_click();

	// Load the default injector and the Application constructor
	const [injector, app_constructor] = <[Injector, Constructor<Application>]> (await Promise.all<any>([
		Promise.require<Injector>("utils/DI", "DefaultInjector"),
		Promise.require<Constructor<Application>>("client/Application", "Application")
	]));

	// Construct the Application
	const app = injector.get(app_constructor);

	try {
		// Assign to global object and call main()
		(<any> window).GuildTools = app;
		await app.main();
	} catch (e) {
		console.error("Loading failed", e);
	}
}
