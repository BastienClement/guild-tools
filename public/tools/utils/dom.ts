/**
 * Simple wrapper around Element#querySelector
 */
export function $(selector: string, parent: Element | Document = document): Element {
	return parent.querySelector(selector);
}
