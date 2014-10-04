GuildTools.controller("LoginCtrl", function($scope, $location) {
	if ($.user) {
		$location.path("/dashboard").replace();
		return;
	}
	
	$scope.setContext();

	$scope.inflight = false;
	$scope.user = localStorage.getItem("login.user");
	$scope.pass = "";

	$scope.$watch("user", function(n, o) {
		if (n != o) localStorage.setItem("login.user", n || "");
	});

	$scope.do_login = function() {
		if ($scope.inflight) return;
		$scope.inflight = true;

		$.call("login:prepare", { user: $scope.user }, function(err, res) {
			if (!res) {
				$scope.inflight = false;
				$scope.user = "";
				$scope.pass = "";
				_("#login-user").focus();
				return;
			}

			var pass = CryptoJS.MD5(phpbb_hash($scope.pass, res.setting) + res.salt).toString();
			$.call("login:exec", { user: $scope.user, pass: pass }, function(err, res) {
				$scope.inflight = false;

				if (res && res.session) {
					localStorage.setItem("session.token", res.session);
					$.wsAuth(true);
				} else {
					$scope.pass = "";
					_("#login-pass").focus();
				}
			});
		});
	};
});
