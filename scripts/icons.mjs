#!/usr/bin/env node
/**
 * Generates the PWA icons (PNG, no external dependencies — zlib from Node).
 * Draws a rounded navy tile with a white bus glyph.
 */
import fs from 'node:fs/promises';
import path from 'node:path';
import zlib from 'node:zlib';

const OUT = path.resolve(import.meta.dirname, '..', 'public', 'icons');
const SS = 4; // supersampling factor

// ---------- tiny PNG encoder ----------
const CRC_TABLE = (() => {
  const t = new Int32Array(256);
  for (let n = 0; n < 256; n++) {
    let c = n;
    for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    t[n] = c;
  }
  return t;
})();
function crc32(buf) {
  let c = -1;
  for (let i = 0; i < buf.length; i++) c = CRC_TABLE[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
  return (c ^ -1) >>> 0;
}
function chunk(type, data) {
  const len = Buffer.alloc(4);
  len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, 'ascii'), data]);
  const crc = Buffer.alloc(4);
  crc.writeUInt32BE(crc32(body));
  return Buffer.concat([len, body, crc]);
}
function encodePng(width, height, rgba) {
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  const raw = Buffer.alloc((width * 4 + 1) * height);
  for (let y = 0; y < height; y++) {
    raw[y * (width * 4 + 1)] = 0; // filter none
    rgba.copy(raw, y * (width * 4 + 1) + 1, y * width * 4, (y + 1) * width * 4);
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(raw, { level: 9 })),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// ---------- drawing ----------
const hex = (s) => [
  parseInt(s.slice(1, 3), 16),
  parseInt(s.slice(3, 5), 16),
  parseInt(s.slice(5, 7), 16),
];

function drawIcon(size, { maskable = false } = {}) {
  const w = size * SS;
  const buf = Buffer.alloc(w * w * 4);
  const bgTop = hex('#24365e');
  const bgBottom = hex('#141d33');
  const white = [255, 255, 255];
  const navy = hex('#182642');
  const yellow = hex('#ffc63a');

  // in maskable mode the glyph must stay inside a safe zone (80% -> 10% margin)
  const scale = maskable ? 0.72 : 1.0;
  const cx = w / 2;
  const cy = w / 2;

  const R = w * 0.22 * (maskable ? 1 / 0.8 : 1); // corner radius (full tile)
  const inCornerRound = (x, y) => {
    const pad = 0;
    const x0 = pad, y0 = pad, x1 = w - pad, y1 = w - pad;
    if (x < x0 || x > x1 || y < y0 || y > y1) return false;
    const r = R;
    const dx = Math.max(x0 + r - x, x - (x1 - r), 0);
    const dy = Math.max(y0 + r - y, y - (y1 - r), 0);
    return dx * dx + dy * dy <= r * r;
  };

  // bus glyph geometry (in unit coords 0..1, then scaled)
  const g = (v) => cy + (v - 0.5) * w * scale;
  const h = (v) => cx + (v - 0.5) * w * scale;
  const bodyX0 = h(0.28), bodyX1 = h(0.72);
  const bodyY0 = g(0.30), bodyY1 = g(0.76);
  const bodyR = w * 0.045 * scale;
  const inBody = (x, y) => {
    if (x < bodyX0 || x > bodyX1 || y < bodyY0 || y > bodyY1) return false;
    const dx = Math.max(bodyX0 + bodyR - x, x - (bodyX1 - bodyR), 0);
    const dy = Math.max(bodyY0 + bodyR - y, y - (bodyY1 - bodyR), 0);
    return dx * dx + dy * dy <= bodyR * bodyR;
  };
  // windshield band
  const winY0 = g(0.355), winY1 = g(0.50);
  const winX0 = h(0.325), winX1 = h(0.675);
  const winR = w * 0.018 * scale;
  const inWindow = (x, y) => {
    if (x < winX0 || x > winX1 || y < winY0 || y > winY1) return false;
    const dx = Math.max(winX0 + winR - x, x - (winX1 - winR), 0);
    const dy = Math.max(winY0 + winR - y, y - (winY1 - winR), 0);
    return dx * dx + dy * dy <= winR * winR;
  };
  // headlights
  const lightR = w * 0.028 * scale;
  const lights = [
    [h(0.345), g(0.575)],
    [h(0.655), g(0.575)],
  ];
  const inLight = (x, y) =>
    lights.some(([lx, ly]) => (x - lx) ** 2 + (y - ly) ** 2 <= lightR * lightR);
  // wheels
  const wheelR = w * 0.062 * scale;
  const wheels = [
    [h(0.40), g(0.78)],
    [h(0.60), g(0.78)],
  ];
  const inWheel = (x, y) =>
    wheels.some(([lx, ly]) => (x - lx) ** 2 + (y - ly) ** 2 <= wheelR * wheelR);
  const inHub = (x, y) =>
    wheels.some(([lx, ly]) => (x - lx) ** 2 + (y - ly) ** 2 <= (wheelR * 0.45) ** 2);

  for (let py = 0; py < size; py++) {
    for (let px = 0; px < size; px++) {
      let r = 0, gg = 0, b = 0, a = 0;
      for (let sy = 0; sy < SS; sy++) {
        for (let sx = 0; sx < SS; sx++) {
          const x = px * SS + sx + 0.5;
          const y = py * SS + sy + 0.5;
          let cr = 0, cg = 0, cb = 0, ca = 0;
          if (inCornerRound(x, y)) {
            const t = y / w;
            cr = Math.round(bgTop[0] + (bgBottom[0] - bgTop[0]) * t);
            cg = Math.round(bgTop[1] + (bgBottom[1] - bgTop[1]) * t);
            cb = Math.round(bgTop[2] + (bgBottom[2] - bgTop[2]) * t);
            ca = 255;
            if (inWheel(x, y)) { cr = navy[0]; cg = navy[1]; cb = navy[2]; }
            if (inBody(x, y)) { cr = white[0]; cg = white[1]; cb = white[2]; }
            if (inBody(x, y) && inWindow(x, y)) { cr = navy[0]; cg = navy[1]; cb = navy[2]; }
            if (inWheel(x, y) && inHub(x, y)) { cr = 220; cg = 224; cb = 232; }
            if (inLight(x, y)) { cr = yellow[0]; cg = yellow[1]; cb = yellow[2]; }
          }
          cr *= ca / 255; cg *= ca / 255; cb *= ca / 255;
          r += cr; gg += cg; b += cb; a += ca;
        }
      }
      const n = SS * SS;
      const i = (py * size + px) * 4;
      buf[i] = Math.round(r / n);
      buf[i + 1] = Math.round(gg / n);
      buf[i + 2] = Math.round(b / n);
      buf[i + 3] = Math.round(a / n);
    }
  }
  return encodePng(size, size, buf);
}

const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 512 512">
  <defs><linearGradient id="g" x1="0" y1="0" x2="0" y2="1">
    <stop offset="0" stop-color="#24365e"/><stop offset="1" stop-color="#141d33"/>
  </linearGradient></defs>
  <rect width="512" height="512" rx="112" fill="url(#g)"/>
  <rect x="143" y="154" width="226" height="236" rx="23" fill="#fff"/>
  <rect x="166" y="182" width="180" height="74" rx="9" fill="#182642"/>
  <circle cx="205" cy="399" r="32" fill="#182642"/>
  <circle cx="307" cy="399" r="32" fill="#182642"/>
  <circle cx="205" cy="399" r="14" fill="#dce0e8"/>
  <circle cx="307" cy="399" r="14" fill="#dce0e8"/>
  <circle cx="177" cy="294" r="14" fill="#ffc63a"/>
  <circle cx="335" cy="294" r="14" fill="#ffc63a"/>
</svg>`;

async function main() {
  await fs.mkdir(OUT, { recursive: true });
  const sizes = [
    [192, 'icon-192.png', {}],
    [512, 'icon-512.png', {}],
    [512, 'maskable-512.png', { maskable: true }],
    [180, 'apple-touch-icon.png', {}],
  ];
  for (const [size, name, opts] of sizes) {
    await fs.writeFile(path.join(OUT, name), drawIcon(size, opts));
    console.log('wrote', name);
  }
  await fs.writeFile(path.join(OUT, 'icon.svg'), svg);
  console.log('wrote icon.svg');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
