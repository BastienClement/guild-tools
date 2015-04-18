declare module "pako" {
	export function deflate(buf: ArrayBuffer): ArrayBuffer;
	export function inflate(buf: ArrayBuffer): ArrayBuffer;
}
