import { $ } from "utils/dom";

/**
 * Object describing an action available on the fullscreen dialog
 */
export interface DialogActions {
	label: string;
	action: () => void;
}

/**
 * Full screen error dialog
 */
let error_visible = false;
export function error(title: string, message: string, infos?: string, actions?: DialogActions[]) {
	if (error_visible) return;
	error_visible = true;
	
	const error = <HTMLDivElement> $("#error");
	
	// Title, messages and infos
	$(".title", error).textContent = title;
	$(".text", error).textContent = message;
	$(".infos", error).textContent = infos ? infos : "";
	
	// Removes actions
	const actions_container = <HTMLDivElement> $(".actions", error);
	actions_container.innerHTML = "";
	
	if (actions) {
		// Closure to capture the label variable
		function create_action(da: DialogActions) {
			const link = document.createElement("a");
			link.innerText = da.label;
			link.onclick = () => da.action();
			actions_container.appendChild(link);
		}

		for (let label of actions) create_action(label);
	}
	
	error.style.display = "block";
}

/**
 * Small status pop-up
 */
let status_timeout: number = null;
export function status(message?: string, sticky: boolean = false) {
	// TODO: move to component
	/*const frame = <HTMLDivElement> $("#status");
	if (message) {
		clearTimeout(status_timeout);
		frame.innerText = message;
		frame.classList.add("visible");
		if (!sticky) {
			status_timeout = setTimeout(() => status(), 2000);
		}
	} else {
		clearTimeout(status_timeout);
		status_timeout = null;
		frame.classList.remove("visible");
	}*/
}
