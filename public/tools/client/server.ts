import { Socket, SocketDelegate } from "gtp3/socket";
import { Deferred } from "utils/deferred";
import { XHRText } from "utils/xhr";

class ServerInterface {
	socket: Socket;
	private connect_deferred: Deferred<Socket>;

	connect() {
		return XHRText("/api/socket_url").then(url => {
			this.connect_deferred = new Deferred<Socket>();
			this.socket = new Socket(url, <any>this);
			this.socket.connect();
			return this.connect_deferred.promise;
		});
	}

	private connected() {
		this.connect_deferred.resolve(this.socket);
	}

	private reconnecting() {

	}

	private disconnected() {
		this.connect_deferred.reject(new Error());
	}

	private reset() {

	}

	private updateLatency() {

	}

	private openChannel() {

	}
}

export const Server = new ServerInterface();
