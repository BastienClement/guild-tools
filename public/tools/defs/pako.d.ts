interface PakoInterface {
	deflate(buf: Uint8Array): Uint8Array;
	inflate(buf: Uint8Array, config?: any): Uint8Array;
}

declare const pako: PakoInterface;
