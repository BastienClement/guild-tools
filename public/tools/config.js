requirejs.config({
	baseUrl: "/assets/modules",
	paths: {
		"pako": "/assets/javascripts/pako",
		"less": "/assets/javascripts/less",
		"encoding": "/assets/javascripts/encoding"
	},
	shim: {
		"pako": { exports: "pako" },
		"encoding": { exports: "Object" }
	}
});
