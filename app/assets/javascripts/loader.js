
gt = {
	loadWeb() { rt_import("web/main.js"); },
	loadClient() { rt_import("client/main.js"); }
};

/**
 * Hacky work-around for the rigidity of import statements
 */
function rt_import(file) {
	var path = __moduleName.split("/");
	path.pop();
	System.get(path.join("/") + "/" + file);
}
