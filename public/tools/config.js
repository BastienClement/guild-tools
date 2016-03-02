System.config({
	paths: {
		"encoding": "/assets/javascripts/encoding.js",
		"phpbb_hash": "/assets/javascripts/phpbb_hash.js",
		"cryptojs": "/assets/javascripts/crypto.js",
		"moment": "/assets/javascripts/moment.js",
		"marked": "/assets/javascripts/marked.js"
	},
	shim: {
		"pako": { exports: "pako" },
		"encoding": { exports: "Object" },
		"phpbb_hash": { deps: ["cryptojs"], exports: "phpbb_hash" },
		"cryptojs": { exports: "CryptoJS" }
	}
});
