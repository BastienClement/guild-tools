interface TextEncoder {
	encoding: string;
	encode(buffer: string, options?: { stream: boolean }): Uint8Array;
}

interface TextDecoder {
	encoding: string;
	decode(buffer?: ArrayBufferView, options?: { stream: boolean }): string;
}

declare var TextEncoder: {
	new(encoding: string): TextEncoder;
};

declare var TextDecoder: {
	new(encoding: string): TextDecoder;
};
