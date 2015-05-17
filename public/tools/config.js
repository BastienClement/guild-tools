requirejs.config({
	baseUrl: "/assets/modules",
	paths: {
		"pako": "/assets/javascripts/pako",
		"less": "/assets/javascripts/less"
	},
	shim: {
		"pako": { exports: "pako" }
	}
});
