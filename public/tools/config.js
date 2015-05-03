requirejs.config({
	baseUrl: "/assets/modules",
	paths: {
		"pako": "/assets/javascripts/pako"
	},
	shim: {
		"pako": {
			exports: "pako"
		}
	}
});
