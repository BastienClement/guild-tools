/**
 * Polymer adapter
 */
export function polymer(selector: string, bundle?: string) {
	return (target: Function) => PolymerLoader.register(selector, bundle, target);
}
