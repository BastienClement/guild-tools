requirejs.config({
	baseUrl: "/assets/modules",
	paths: {

	}
});

if (true) {
	require(["web/init"], function(init) {
		init();
	});
}
