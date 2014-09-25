/*
	Copyright (c) 2013 Bastien Clément

	Permission is hereby granted, free of charge, to any person obtaining a
	copy of this software and associated documentation files (the
	"Software"), to deal in the Software without restriction, including
	without limitation the rights to use, copy, modify, merge, publish,
	distribute, sublicense, and/or sell copies of the Software, and to
	permit persons to whom the Software is furnished to do so, subject to
	the following conditions:

	The above copyright notice and this permission notice shall be included
	in all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
	OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
	MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
	IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
	CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
	TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
	SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
*/

var phpbb_hash = (function() {
	function md5(data, raw) {
		var hash = CryptoJS.MD5(data);

		if (raw) {
			return hash;
		}

		return hash.toString();
	}

	//
	// phpBB Hash
	//
	var itoa64 = "./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

	function phpbb_hash(password, setting) {
		var hash = _hash_crypt_private(password, setting);

		if (hash.length == 34) {
			return hash;
		}

		return md5(password);
	}

	function _hash_encode64(input, count) {
		var output = '';
		var i = 0;

		// Convert
		input = input.toByteArray();

		do {
			var value = input[i++];
			output += itoa64[value & 0x3f];

			if (i < count) {
				value = value | input[i] << 8;
			}

			output += itoa64[(value >> 6) & 0x3f];

			if (i++ >= count) {
				break;
			}

			if (i < count) {
				value = value | input[i] << 16;
			}

			output += itoa64[(value >> 12) & 0x3f];

			if (i++ >= count) {
				break;
			}

			output += itoa64[(value >> 18) & 0x3f];
		} while (i < count);

		return output;
	}


	function _hash_crypt_private(password, setting) {
		var output = "*";

		// Check for correct hash
		if (setting.substr(0, 3) != '$H$') {
			return output;
		}

		var count_log2 = itoa64.indexOf(setting[3]);

		if (count_log2 < 7 || count_log2 > 30) {
			return output;
		}

		var count = 1 << count_log2;
		var salt = setting.substr(4, 8);

		if (salt.length != 8) {
			return output;
		}

		var passBytes = CryptoJS.enc.Utf8.parse(password);
		var hash = md5(salt + password, true);
		do {
			hash = md5(hash.concat(passBytes), true);	
		} while (--count);

		output = setting.substr(0, 12);
		output += _hash_encode64(hash, 16);

		return output;
	}

	return phpbb_hash;
})();
