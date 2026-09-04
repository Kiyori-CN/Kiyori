import type { QueryParams } from "./types";

const MIXIN_KEY_ENC_TAB = [
  46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35,
  27, 43, 5, 49, 33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13,
  37, 48, 7, 16, 24, 55, 40, 61, 26, 17, 0, 1, 60, 51, 30, 4,
  22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11, 36, 20, 34, 44, 52
] as const;

const ROTATIONS = [
  7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
  5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
  4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
  6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21
] as const;

function rotateLeft(value: number, shift: number): number {
  return ((value << shift) | (value >>> (32 - shift))) >>> 0;
}

function littleEndianHex(value: number): string {
  let output = "";
  for (let offset = 0; offset < 32; offset += 8) {
    output += ((value >>> offset) & 0xff).toString(16).padStart(2, "0");
  }
  return output;
}

export function md5Ascii(input: string): string {
  const inputBytes: number[] = [];
  for (let index = 0; index < input.length; index += 1) {
    const code = input.charCodeAt(index);
    if (code > 0x7f) {
      throw new Error("MD5 input must be ASCII after URL encoding.");
    }
    inputBytes.push(code);
  }
  const bitLength = inputBytes.length * 8;
  inputBytes.push(0x80);
  while (inputBytes.length % 64 !== 56) {
    inputBytes.push(0);
  }
  const lowBits = bitLength >>> 0;
  const highBits = Math.floor(bitLength / 0x100000000) >>> 0;
  for (let offset = 0; offset < 32; offset += 8) {
    inputBytes.push((lowBits >>> offset) & 0xff);
  }
  for (let offset = 0; offset < 32; offset += 8) {
    inputBytes.push((highBits >>> offset) & 0xff);
  }

  let a0 = 0x67452301;
  let b0 = 0xefcdab89;
  let c0 = 0x98badcfe;
  let d0 = 0x10325476;
  const constants = Array.from(
    { length: 64 },
    (_, index) => Math.floor(Math.abs(Math.sin(index + 1)) * 0x100000000) >>> 0
  );

  for (let block = 0; block < inputBytes.length; block += 64) {
    const words = new Array<number>(16);
    for (let index = 0; index < 16; index += 1) {
      const position = block + index * 4;
      words[index] =
        (inputBytes[position] |
          (inputBytes[position + 1] << 8) |
          (inputBytes[position + 2] << 16) |
          (inputBytes[position + 3] << 24)) >>> 0;
    }
    let a = a0;
    let b = b0;
    let c = c0;
    let d = d0;
    for (let index = 0; index < 64; index += 1) {
      let f: number;
      let wordIndex: number;
      if (index < 16) {
        f = (b & c) | (~b & d);
        wordIndex = index;
      } else if (index < 32) {
        f = (d & b) | (~d & c);
        wordIndex = (5 * index + 1) % 16;
      } else if (index < 48) {
        f = b ^ c ^ d;
        wordIndex = (3 * index + 5) % 16;
      } else {
        f = c ^ (b | ~d);
        wordIndex = (7 * index) % 16;
      }
      const previousD = d;
      d = c;
      c = b;
      const sum = (a + f + constants[index] + words[wordIndex]) >>> 0;
      b = (b + rotateLeft(sum, ROTATIONS[index])) >>> 0;
      a = previousD;
    }
    a0 = (a0 + a) >>> 0;
    b0 = (b0 + b) >>> 0;
    c0 = (c0 + c) >>> 0;
    d0 = (d0 + d) >>> 0;
  }
  return littleEndianHex(a0) + littleEndianHex(b0) + littleEndianHex(c0) + littleEndianHex(d0);
}

export function encodeQueryPart(value: string): string {
  return encodeURIComponent(value).replace(/%20/g, "+");
}

export function buildQuery(params: QueryParams): string {
  return Object.keys(params)
    .sort()
    .filter((key) => params[key] !== undefined)
    .map((key) => {
      const rawValue = String(params[key]).replace(/[!'()*]/g, "");
      return encodeQueryPart(key) + "=" + encodeQueryPart(rawValue);
    })
    .join("&");
}

export function wbiMixinKey(imgKey: string, subKey: string): string {
  const original = imgKey + subKey;
  if (original.length < 64) {
    throw new Error("WBI image keys are incomplete.");
  }
  return MIXIN_KEY_ENC_TAB.map((index) => original[index]).join("").slice(0, 32);
}

export function signWbi(
  params: QueryParams,
  imgKey: string,
  subKey: string,
  timestampSeconds: number
): string {
  const values: QueryParams = { ...params, wts: Math.trunc(timestampSeconds) };
  const query = buildQuery(values);
  return query + "&w_rid=" + md5Ascii(query + wbiMixinKey(imgKey, subKey));
}
