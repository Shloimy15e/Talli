// Generate a 512x512 PNG icon for the Raycast extension.
// Reuses the same bar chart logo as the Chrome extension.
// Run: node generate-icon.js

const fs = require('fs');

function generatePng(size) {
  const pixels = new Uint8Array(size * size * 4);
  const r = 234, g = 124, b = 40, a = 255;
  const scale = size / 64;

  function drawBar(x, y, w, h) {
    const sx = Math.round(x * scale);
    const sy = Math.round(y * scale);
    const sw = Math.max(Math.round(w * scale), 1);
    const sh = Math.max(Math.round(h * scale), 1);
    const radius = Math.max(Math.round(2 * scale), 1);

    for (let py = sy; py < sy + sh && py < size; py++) {
      for (let px = sx; px < sx + sw && px < size; px++) {
        const inTL = py - sy < radius && px - sx < radius;
        const inTR = py - sy < radius && (sx + sw - 1 - px) < radius;
        const inBL = (sy + sh - 1 - py) < radius && px - sx < radius;
        const inBR = (sy + sh - 1 - py) < radius && (sx + sw - 1 - px) < radius;
        let draw = true;
        if (inTL) { const dx = radius-(px-sx)-0.5, dy = radius-(py-sy)-0.5; if (dx*dx+dy*dy > radius*radius) draw=false; }
        if (inTR) { const dx = radius-(sx+sw-1-px)-0.5, dy = radius-(py-sy)-0.5; if (dx*dx+dy*dy > radius*radius) draw=false; }
        if (inBL) { const dx = radius-(px-sx)-0.5, dy = radius-(sy+sh-1-py)-0.5; if (dx*dx+dy*dy > radius*radius) draw=false; }
        if (inBR) { const dx = radius-(sx+sw-1-px)-0.5, dy = radius-(sy+sh-1-py)-0.5; if (dx*dx+dy*dy > radius*radius) draw=false; }
        if (draw) {
          const idx = (py * size + px) * 4;
          pixels[idx] = r; pixels[idx+1] = g; pixels[idx+2] = b; pixels[idx+3] = a;
        }
      }
    }
  }

  drawBar(16, 36, 8, 16);
  drawBar(28, 24, 8, 28);
  drawBar(40, 12, 8, 40);
  return encodePng(size, size, pixels);
}

function encodePng(width, height, rgba) {
  function crc32(buf) {
    let c = 0xffffffff;
    const table = new Int32Array(256);
    for (let n = 0; n < 256; n++) { let v = n; for (let k = 0; k < 8; k++) v = v & 1 ? 0xedb88320 ^ (v >>> 1) : v >>> 1; table[n] = v; }
    for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  }
  function adler32(buf) {
    let a = 1, b = 0;
    for (let i = 0; i < buf.length; i++) { a = (a + buf[i]) % 65521; b = (b + a) % 65521; }
    return ((b << 16) | a) >>> 0;
  }
  function chunk(type, data) {
    const len = data.length, buf = Buffer.alloc(12 + len);
    buf.writeUInt32BE(len, 0); buf.write(type, 4, 'ascii'); data.copy(buf, 8);
    const crcData = Buffer.alloc(4 + len); crcData.write(type, 0, 'ascii'); data.copy(crcData, 4);
    buf.writeUInt32BE(crc32(crcData), 8 + len); return buf;
  }
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0); ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
  const rawSize = height * (1 + width * 4), raw = Buffer.alloc(rawSize);
  let offset = 0;
  for (let y = 0; y < height; y++) { raw[offset++] = 0; for (let x = 0; x < width; x++) { const i = (y * width + x) * 4; raw[offset++] = rgba[i]; raw[offset++] = rgba[i+1]; raw[offset++] = rgba[i+2]; raw[offset++] = rgba[i+3]; } }
  const blocks = []; let pos = 0;
  while (pos < raw.length) {
    const blockLen = Math.min(65535, raw.length - pos), last = pos + blockLen >= raw.length ? 1 : 0;
    const header = Buffer.alloc(5); header[0] = last; header.writeUInt16LE(blockLen, 1); header.writeUInt16LE(blockLen ^ 0xffff, 3);
    blocks.push(header, raw.slice(pos, pos + blockLen)); pos += blockLen;
  }
  const deflated = Buffer.concat([Buffer.from([0x78, 0x01]), ...blocks, (() => { const b = Buffer.alloc(4); b.writeUInt32BE(adler32(raw)); return b; })()]);
  return Buffer.concat([Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]), chunk('IHDR', ihdr), chunk('IDAT', deflated), chunk('IEND', Buffer.alloc(0))]);
}

const png = generatePng(512);
fs.mkdirSync('assets', { recursive: true });
fs.writeFileSync('assets/extension-icon.png', png);
console.log(`Generated assets/extension-icon.png (${png.length} bytes)`);
