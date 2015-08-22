requirejs.config({
	baseUrl: "/assets/modules",
	paths: {
		"pako": "/assets/javascripts/pako",
		"less": "/assets/javascripts/less",
		"encoding": "/assets/javascripts/encoding",
		"phpbb_hash": "/assets/javascripts/phpbb_hash",
		"cryptojs": "/assets/javascripts/crypto",
		"moment": "/assets/javascripts/moment",
	},
	shim: {
		"pako": { exports: "pako" },
		"encoding": { exports: "Object" },
		"phpbb_hash": { deps: ["cryptojs"], exports: "phpbb_hash" },
		"cryptojs": { exports: "CryptoJS" }
	}
});
