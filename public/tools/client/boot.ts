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
function boot() {
	// Fix the html imports shim
	fix_imports_shim();
	
	let DeferredLazy: typeof Deferred = null;
	loading_less.then((source: string) => {
		return less.render(source);
	}).then((res: LessRenderResults) => {
		const style = document.createElement("style");
		style.type = "text/css";
		style.appendChild(document.createTextNode(res.css));
		document.head.appendChild(style);
	}).then(() => new Promise((resolve, reject) => {
		require(["utils/deferred"], (mod: any) => resolve(DeferredLazy = mod.Deferred));
	})).then(() => {
		return DeferredLazy.require<Injector>("utils/di", "DefaultInjector");
	}).then((injector: Injector) => {
		return DeferredLazy.require<Constructor<Application>>("client/main", "Application").then(Application => injector.get(Application));
	}).then((app: Application) => {
		GuildTools = app;
		app.main();
	});
}

export = boot;
