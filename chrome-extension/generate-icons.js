// Quick script to generate PNG icons from SVG for the Chrome extension.
// Run: node generate-icons.js
// Requires no dependencies — writes raw PNG data.

const fs = require('fs');
const { createCanvas } = (() => {
  // Try to use canvas package, fall back to manual PNG generation
  try {
    return require('canvas');
  } catch {
    return { createCanvas: null };
  }
})();

// Simple PNG generator for solid-color bar chart icon
function generatePng(size) {
  // Create a minimal PNG with the Talli bar chart logo
  // Since we can't easily do canvas without deps, generate a simple colored square icon
  // with the bars drawn pixel by pixel

  const pixels = new Uint8Array(size * size * 4); // RGBA

  // Background: transparent
  // Draw three orange bars (#ea7c28 = 234, 124, 40)
  const r = 234, g = 124, b = 40, a = 255;

  // Scale bars proportionally to icon size
  // Original: 64x64, bars at x=16,28,40 width=8, heights=16,28,40 from bottom
  const scale = size / 64;

  function drawBar(x, y, w, h) {
    const sx = Math.round(x * scale);
    const sy = Math.round(y * scale);
    const sw = Math.max(Math.round(w * scale), 1);
    const sh = Math.max(Math.round(h * scale), 1);
    const radius = Math.max(Math.round(2 * scale), 1);

    for (let py = sy; py < sy + sh && py < size; py++) {
      for (let px = sx; px < sx + sw && px < size; px++) {
        // Simple rounded corners check
        const inTopLeft = py - sy < radius && px - sx < radius;
        const inTopRight = py - sy < radius && (sx + sw - 1 - px) < radius;
        const inBotLeft = (sy + sh - 1 - py) < radius && px - sx < radius;
        const inBotRight = (sy + sh - 1 - py) < radius && (sx + sw - 1 - px) < radius;

        let draw = true;
        if (inTopLeft) {
          const dx = radius - (px - sx) - 0.5;
          const dy = radius - (py - sy) - 0.5;
          if (dx * dx + dy * dy > radius * radius) draw = false;
        }
        if (inTopRight) {
          const dx = radius - (sx + sw - 1 - px) - 0.5;
          const dy = radius - (py - sy) - 0.5;
          if (dx * dx + dy * dy > radius * radius) draw = false;
        }
        if (inBotLeft) {
          const dx = radius - (px - sx) - 0.5;
          const dy = radius - (sy + sh - 1 - py) - 0.5;
          if (dx * dx + dy * dy > radius * radius) draw = false;
        }
        if (inBotRight) {
          const dx = radius - (sx + sw - 1 - px) - 0.5;
          const dy = radius - (sy + sh - 1 - py) - 0.5;
          if (dx * dx + dy * dy > radius * radius) draw = false;
        }

        if (draw) {
          const idx = (py * size + px) * 4;
          pixels[idx] = r;
          pixels[idx + 1] = g;
          pixels[idx + 2] = b;
          pixels[idx + 3] = a;
        }
      }
    }
  }

  // Three bars from the logo SVG
  drawBar(16, 36, 8, 16);  // short bar
  drawBar(28, 24, 8, 28);  // medium bar
  drawBar(40, 12, 8, 40);  // tall bar

  return encodePng(size, size, pixels);
}

// Minimal PNG encoder (no dependencies)
function encodePng(width, height, rgba) {
  function crc32(buf) {
    let c = 0xffffffff;
    const table = new Int32Array(256);
    for (let n = 0; n < 256; n++) {
      let v = n;
      for (let k = 0; k < 8; k++) v = v & 1 ? 0xedb88320 ^ (v >>> 1) : v >>> 1;
      table[n] = v;
    }
    for (let i = 0; i < buf.length; i++) c = table[(c ^ buf[i]) & 0xff] ^ (c >>> 8);
    return (c ^ 0xffffffff) >>> 0;
  }

  function adler32(buf) {
    let a = 1, b = 0;
    for (let i = 0; i < buf.length; i++) {
      a = (a + buf[i]) % 65521;
      b = (b + a) % 65521;
    }
    return ((b << 16) | a) >>> 0;
  }

  function chunk(type, data) {
    const len = data.length;
    const buf = Buffer.alloc(12 + len);
    buf.writeUInt32BE(len, 0);
    buf.write(type, 4, 'ascii');
    data.copy(buf, 8);
    const crcData = Buffer.alloc(4 + len);
    crcData.write(type, 0, 'ascii');
    data.copy(crcData, 4);
    buf.writeUInt32BE(crc32(crcData), 8 + len);
    return buf;
  }

  // IHDR
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 6; // RGBA
  ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;

  // Raw image data with filter byte (0 = None) per row
  const rawSize = height * (1 + width * 4);
  const raw = Buffer.alloc(rawSize);
  let offset = 0;
  for (let y = 0; y < height; y++) {
    raw[offset++] = 0; // filter: None
    for (let x = 0; x < width; x++) {
      const i = (y * width + x) * 4;
      raw[offset++] = rgba[i];
      raw[offset++] = rgba[i + 1];
      raw[offset++] = rgba[i + 2];
      raw[offset++] = rgba[i + 3];
    }
  }

  // Deflate (store only, no compression — small icons don't need it)
  const blocks = [];
  let pos = 0;
  while (pos < raw.length) {
    const blockLen = Math.min(65535, raw.length - pos);
    const last = pos + blockLen >= raw.length ? 1 : 0;
    const header = Buffer.alloc(5);
    header[0] = last;
    header.writeUInt16LE(blockLen, 1);
    header.writeUInt16LE(blockLen ^ 0xffff, 3);
    blocks.push(header, raw.slice(pos, pos + blockLen));
    pos += blockLen;
  }

  const deflated = Buffer.concat([
    Buffer.from([0x78, 0x01]), // zlib header
    ...blocks,
    (() => { const b = Buffer.alloc(4); b.writeUInt32BE(adler32(raw)); return b; })()
  ]);

  const signature = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
  const iend = chunk('IEND', Buffer.alloc(0));

  return Buffer.concat([
    signature,
    chunk('IHDR', ihdr),
    chunk('IDAT', deflated),
    iend
  ]);
}

// Generate all three sizes
[16, 48, 128].forEach(size => {
  const png = generatePng(size);
  const path = `icons/icon${size}.png`;
  fs.writeFileSync(path, png);
  console.log(`Generated ${path} (${png.length} bytes)`);
});
