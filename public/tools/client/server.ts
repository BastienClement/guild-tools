import { Socket, SocketDelegate } from "gtp3/socket";
import { Deferred } from "utils/deferred";
import { XHRText } from "utils/xhr";

class ServerInterface {
	socket: Socket;

	connect() {
		XHRText("/api/socket_url").then(url => console.log(url));
	}
}

export const Server = new ServerInterface();
