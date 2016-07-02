import {Component} from "../../utils/DI";
import {ServiceWorker} from "../../utils/Worker";
import {Loader} from "./Loader";

// Shared library path
const SHARED_LIB = "/assets/less/lib.less";

/**
 * The Less compiler component of the Loader.
 * This class implements sugars available in LESS files.
 */
@Component
export class LessCompiler {
	/** The Less worker used to compile source off the main thread */
	private lessWorker = new ServiceWorker("/assets/workers/less.js");

	/**
	 * Compiles LESS code to CSS.
	 * @param source    the LESS source code
	 * @param loader    the calling loader used to load dependencies
	 */
	public async compile(source: string, loader: Loader): Promise<string> {
		// Prepend the source with the global library
		source = `
			@import (dynamic) "${SHARED_LIB}";
			${source}
		`;

		let source_css = await this.importDynamics(source, loader);
		let result_css = await this.lessWorker.request<string>("compile", source_css);

		return StyleFix.fix(result_css, true);
	}

	/**
	 * Imports dynamic imports.
	 * @param source    the LESS source
	 * @param loader    the calling loader used to load dependencies
	 */
	private async importDynamics(source: string, loader: Loader): Promise<string> {
		// Split the input file on every dynamic import
		let parts = source.split(/@import\s*\(dynamic\)\s*"([^"]*)";?/);

		// No import found
		if (parts.length == 1) return source;

		// Fetch imports
		let dyn_imports: Promise<string>[] = [];
		for (let i = 1; i < parts.length; ++i) {
			if (i % 2 == 1) dyn_imports.push(loader.fetch(parts[i]));
		}

		let dyn_sources = await Promise.all(dyn_imports);
		for (let i = 0; i < dyn_sources.length; i++) {
			parts[i * 2 + 1] = dyn_sources[i];
		}

		// Recursive handling of deep @import (dynamic)
		return await this.importDynamics(parts.join(""), loader);
	}
}
