// Penulis EXIF minimal: GPS + tanggal/jam. Dipakai halaman stempel foto.
(function () {
  const T_BYTE = 1, T_ASCII = 2, T_LONG = 4, T_RATIONAL = 5;

  function dms(v) {
    v = Math.abs(v);
    const d = Math.floor(v);
    const m = Math.floor((v - d) * 60);
    const s = ((v - d) * 60 - m) * 60;
    return [[d, 1], [m, 1], [Math.round(s * 10000), 10000]];
  }

  // entri: {tag, type, count, bytes}
  function buildApp1(opt) {
    const enc = new TextEncoder();
    const ascii = (s) => {
      const b = new Uint8Array(s.length + 1);
      b.set(enc.encode(s));
      b[s.length] = 0;
      return b;
    };

    const lat = dms(opt.lat), lon = dms(opt.lon);
    const dt = ascii(opt.dateTime);          // "YYYY:MM:DD HH:MM:SS"
    const off = ascii(opt.offset || "+07:00");
    const stamp = ascii(opt.dateStamp);      // "YYYY:MM:DD"

    const rat = (arr) => {
      const b = new Uint8Array(24);
      const dv = new DataView(b.buffer);
      arr.forEach(([n, d], i) => { dv.setUint32(i * 8, n, true); dv.setUint32(i * 8 + 4, d, true); });
      return b;
    };
    const byte4 = (a) => new Uint8Array(a);

    const ifd0 = [
      { tag: 0x0132, type: T_ASCII, count: 20, bytes: dt },
      { tag: 0x8769, type: T_LONG, count: 1, bytes: null },   // -> Exif IFD
      { tag: 0x8825, type: T_LONG, count: 1, bytes: null }    // -> GPS IFD
    ];
    const exifIfd = [
      { tag: 0x9003, type: T_ASCII, count: 20, bytes: dt },
      { tag: 0x9004, type: T_ASCII, count: 20, bytes: dt },
      { tag: 0x9011, type: T_ASCII, count: 7, bytes: off },
      { tag: 0x9012, type: T_ASCII, count: 7, bytes: off }
    ];
    const gpsIfd = [
      { tag: 0x0000, type: T_BYTE, count: 4, bytes: byte4([2, 3, 0, 0]) },
      { tag: 0x0001, type: T_ASCII, count: 2, bytes: ascii(opt.lat < 0 ? "S" : "N") },
      { tag: 0x0002, type: T_RATIONAL, count: 3, bytes: rat(lat) },
      { tag: 0x0003, type: T_ASCII, count: 2, bytes: ascii(opt.lon < 0 ? "W" : "E") },
      { tag: 0x0004, type: T_RATIONAL, count: 3, bytes: rat(lon) },
      { tag: 0x001D, type: T_ASCII, count: 11, bytes: stamp }
    ];

    const sizeOf = (n) => 2 + n * 12 + 4;
    const ifd0At = 8;
    const exifAt = ifd0At + sizeOf(ifd0.length);
    const gpsAt = exifAt + sizeOf(exifIfd.length);
    const dataAt = gpsAt + sizeOf(gpsIfd.length);

    // pointer
    ifd0[1].bytes = new Uint8Array(4); new DataView(ifd0[1].bytes.buffer).setUint32(0, exifAt, true);
    ifd0[2].bytes = new Uint8Array(4); new DataView(ifd0[2].bytes.buffer).setUint32(0, gpsAt, true);

    // hitung offset data (nilai > 4 byte)
    let cursor = dataAt;
    const place = (entries) => {
      entries.forEach((e) => {
        if (e.bytes.length > 4) {
          if (cursor % 2) cursor++;
          e.at = cursor;
          cursor += e.bytes.length;
        }
      });
    };
    place(ifd0); place(exifIfd); place(gpsIfd);

    const total = cursor + (cursor % 2);
    const tiff = new Uint8Array(total);
    const dv = new DataView(tiff.buffer);
    tiff[0] = 0x49; tiff[1] = 0x49;              // "II"
    dv.setUint16(2, 0x2a, true);
    dv.setUint32(4, ifd0At, true);

    const writeIfd = (entries, at) => {
      dv.setUint16(at, entries.length, true);
      entries.forEach((e, i) => {
        const p = at + 2 + i * 12;
        dv.setUint16(p, e.tag, true);
        dv.setUint16(p + 2, e.type, true);
        dv.setUint32(p + 4, e.count, true);
        if (e.bytes.length <= 4) {
          tiff.set(e.bytes, p + 8);
        } else {
          dv.setUint32(p + 8, e.at, true);
          tiff.set(e.bytes, e.at);
        }
      });
      dv.setUint32(at + 2 + entries.length * 12, 0, true);
    };
    writeIfd(ifd0, ifd0At); writeIfd(exifIfd, exifAt); writeIfd(gpsIfd, gpsAt);

    const payload = new Uint8Array(6 + tiff.length);
    payload.set(new TextEncoder().encode("Exif\0\0"), 0);   // "Exif" + dua byte nol
    payload[4] = 0; payload[5] = 0;
    payload.set(tiff, 6);

    const app1 = new Uint8Array(4 + payload.length);
    app1[0] = 0xff; app1[1] = 0xe1;
    const len = payload.length + 2;
    app1[2] = (len >> 8) & 0xff; app1[3] = len & 0xff;
    app1.set(payload, 4);
    return app1;
  }

  function stripExif(jpg) {
    // buang APP1 Exif lama; segmen lain (JFIF, thumbnail) dibiarkan
    if (jpg[0] !== 0xff || jpg[1] !== 0xd8) return jpg;
    const out = [jpg.subarray(0, 2)];
    let i = 2;
    while (i < jpg.length - 1) {
      if (jpg[i] !== 0xff) break;
      const m = jpg[i + 1];
      if (m === 0xda) { out.push(jpg.subarray(i)); return concat(out); }
      if (m === 0xd8) { i += 2; continue; }
      const segLen = (jpg[i + 2] << 8) | jpg[i + 3];
      const seg = jpg.subarray(i, i + 2 + segLen);
      const isExif = m === 0xe1 && seg[4] === 0x45 && seg[5] === 0x78 && seg[6] === 0x69 && seg[7] === 0x66;
      if (!isExif) out.push(seg);
      i += 2 + segLen;
    }
    out.push(jpg.subarray(i));
    return concat(out);
  }

  function concat(parts) {
    let n = 0;
    parts.forEach((p) => (n += p.length));
    const out = new Uint8Array(n);
    let o = 0;
    parts.forEach((p) => { out.set(p, o); o += p.length; });
    return out;
  }

  function insertExif(jpg, opt) {
    const clean = stripExif(jpg);
    const app1 = buildApp1(opt);
    const out = new Uint8Array(clean.length + app1.length);
    out.set(clean.subarray(0, 2), 0);
    out.set(app1, 2);
    out.set(clean.subarray(2), 2 + app1.length);
    return out;
  }

  if (typeof module !== "undefined" && module.exports) module.exports = { buildApp1, insertExif };
  else window.ExifWriter = { buildApp1, insertExif };
})();
