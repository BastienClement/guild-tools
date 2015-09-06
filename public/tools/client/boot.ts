import { Deferred } from "utils/deferred";
import { Injector, Constructor } from "utils/di";
import { Application } from "client/main";

/**
 * The global reference to the Application instance
 */
declare var GuildTools: Application;

/**
 * This promise is resolved when loading.less is available
 */
declare const loading_less: Promise<string>;

/**
 * Fix the mess made by the HTMLImports shim
 */
function fix_imports_shim() {
	const parser = (<any>window).HTMLImports.parser;
	if (parser) {
		const add_element = parser.addElementToDocument;
		parser.addElementToDocument = function(elt: Element) {
			add_element.apply(parser, arguments);
			if (elt.nodeName == "STYLE") {
				elt.addEventListener("load", () => {
					const parent = elt.parentNode;
					if (parent) parent.removeChild(elt);
				});
			}
		}
	}	
}

/**
 * Load and initialize Guild Tools
 */
export default async function boot() {
	// Fix the html imports shim
	fix_imports_shim();
	
	let DeferredLazy: typeof Deferred = null;
	
	// Wait for loading.less to be loaded and then compile it
	const loading_less_res = await less.render(await loading_less);
	
	// Insert loading styles in document
	const style = document.createElement("style");
	style.type = "text/css";
	style.appendChild(document.createTextNode(loading_less_res.css));
	document.head.appendChild(style);
	
	// Load the deferred module
	DeferredLazy = (await System.import<{ Deferred: typeof Deferred }>("utils/deferred")).Deferred;
	
	// Load the default injector and the Application constructor
	const injector = await DeferredLazy.require<Injector>("utils/di", "DefaultInjector");
	const app_constructor = await DeferredLazy.require<Constructor<Application>>("client/main", "Application");
	
	// Construct the Application
	const app = injector.get(app_constructor);
	
	// Assign to global object and call main()
	(<any> window).GuildTools = app;
	app.main();
}
