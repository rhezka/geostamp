#!/usr/bin/env python3
"""Stamp a photo with map, address, coordinates and time.

Peta dan alamat diambil dari titik koordinat yang sama, jadi selalu sinkron.
Peta: OpenStreetMap (wajib atribusi). Alamat: Nominatim (OSM).

Contoh:
  python stempel_foto.py --image foto.jpg --out hasil.jpg \
      --lat -0.081101 --lon 109.390513 --datetime "2026-10-16 11:28"
"""

import argparse
import io
import math
import os
import urllib.parse
import urllib.request
from datetime import datetime, timedelta

import piexif
from PIL import Image, ImageDraw, ImageFont, ImageOps

try:  # foto iPhone (.heic)
    from pillow_heif import register_heif_opener
    register_heif_opener()
except Exception:
    pass

UA = "ocm-photo-stamp/1.0 (personal use; contact: local user)"
TILE_CACHE = "/workspace/.opencode/tmp/opencode/tiles"

# gaya peta: url, atribusi wajib, zoom maksimum
TILES = {
    "peta": ("https://tile.openstreetmap.org/{z}/{x}/{y}.png",
             "\u00a9 OpenStreetMap", 19),
    "hijau": ("https://a.tile.opentopomap.org/{z}/{x}/{y}.png",
              "\u00a9 OpenTopoMap (CC-BY-SA)", 16),
    "satelit": ("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}",
                "\u00a9 Esri, Maxar, Earthstar Geographics", 19),
    "terang": ("https://a.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}.png",
               "\u00a9 OpenStreetMap, \u00a9 CARTO", 19),
}
FONT_DIR = "/usr/share/fonts/truetype/liberation"
FONT_R = os.path.join(FONT_DIR, "LiberationSans-Regular.ttf")
FONT_B = os.path.join(FONT_DIR, "LiberationSans-Bold.ttf")

HARI = ["Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu"]
BULAN = ["Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli",
         "Agustus", "September", "Oktober", "November", "Desember"]

# ---------------------------------------------------------------- map tiles

def _get(url, timeout=20):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        return r.read()


def deg2num(lat, lon, z):
    n = 2 ** z
    x = (lon + 180.0) / 360.0 * n
    rad = math.radians(lat)
    y = (1.0 - math.log(math.tan(rad) + 1.0 / math.cos(rad)) / math.pi) / 2.0 * n
    return x, y


def _tile(z, x, y, style="peta"):
    os.makedirs(TILE_CACHE, exist_ok=True)
    path = os.path.join(TILE_CACHE, f"{style}_{z}_{x}_{y}.png")
    if os.path.exists(path) and os.path.getsize(path) > 0:
        return path
    url = TILES.get(style, TILES["peta"])[0].format(z=z, x=x, y=y)
    data = _get(url)
    with open(path, "wb") as f:
        f.write(data)
    return path


def map_image(lat, lon, size, zoom=17, style="peta"):
    """Ambil potongan peta persegi `size` px di sekitar titik, plus posisi pin."""
    px_ratio = min(1.8, size / 260.0)            # tajamkan seperlunya, jangan ambil kebanyakan tile
    style = style if style in TILES else "peta"
    z = min(zoom, TILES[style][2])
    fx, fy = deg2num(lat, lon, z)
    cx, cy = fx * 256.0, fy * 256.0
    half = size * px_ratio / 2.0
    tx0 = int((cx - half) // 256)
    ty0 = int((cy - half) // 256)
    tx1 = int((cx + half) // 256)
    ty1 = int((cy + half) // 256)

    canvas = Image.new("RGB", ((tx1 - tx0 + 1) * 256, (ty1 - ty0 + 1) * 256), (230, 230, 230))
    for tx in range(tx0, tx1 + 1):
        for ty in range(ty0, ty1 + 1):
            try:
                tile = Image.open(_tile(z, tx, ty, style)).convert("RGB")
            except Exception:
                continue
            canvas.paste(tile, ((tx - tx0) * 256, (ty - ty0) * 256))

    left = cx - half - tx0 * 256
    top = cy - half - ty0 * 256
    box = (int(left), int(top), int(left + size * px_ratio), int(top + size * px_ratio))
    crop = canvas.crop(box).resize((size, size), Image.LANCZOS)
    return crop, (size / 2.0, size / 2.0)


def draw_pin(img, center):
    """Pin merah + bayangan biru lembut, gaya seperti contoh."""
    w, h = img.size
    cx, cy = center
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(overlay)
    r = w * 0.055
    # kerucut bayangan biru ke bawah-kiri
    d.polygon([(cx, cy + r * 0.4), (cx - r * 2.6, cy + r * 4.2), (cx - r * 0.4, cy + r * 4.6)],
              fill=(60, 130, 246, 70))
    # badan pin
    top = cy - r * 2.2
    d.ellipse([cx - r, top, cx + r, top + 2 * r], fill=(235, 40, 55, 255),
              outline=(255, 255, 255, 255), width=max(2, int(r * 0.18)))
    d.polygon([(cx - r * 0.72, top + r * 1.35), (cx + r * 0.72, top + r * 1.35), (cx, cy + r * 0.55)],
              fill=(235, 40, 55, 255))
    d.ellipse([cx - r * 0.38, top + r * 0.62, cx + r * 0.38, top + r * 1.38], fill=(255, 255, 255, 255))
    img.alpha_composite(overlay)


# ------------------------------------------------------------------ geocode

def reverse(lat, lon):
    """Nomination + Photon digabung; Photon sering punya nama gang yang tidak ada di Nominatim."""
    import json
    geo, photon = {}, {}
    q = urllib.parse.urlencode({
        "format": "jsonv2", "lat": f"{lat:.6f}", "lon": f"{lon:.6f}",
        "zoom": 18, "addressdetails": 1, "accept-language": "id",
    })
    try:
        geo = json.loads(_get("https://nominatim.openstreetmap.org/reverse?" + q).decode("utf-8", "replace"))
    except Exception:
        pass
    try:
        ph = urllib.parse.urlencode({"lat": f"{lat:.6f}", "lon": f"{lon:.6f}", "limit": 3})
        data = json.loads(_get("https://photon.komoot.io/reverse?" + ph).decode("utf-8", "replace"))
        feats = data.get("features") or []
        if feats:
            photon = feats[0].get("properties") or {}
    except Exception:
        pass
    return geo, photon


def _region_name(a):
    """Nama wilayah untuk baris judul: Kota X untuk kota, Kabupaten X untuk kabupaten."""
    city = a.get("city") or a.get("town")
    district = a.get("city_district") or a.get("suburb") or ""
    county = a.get("county")

    # kota biasanya punya kecamatan yang namanya diawali nama kota, mis. "Pontianak Selatan"
    if city and district and district.lower().startswith(city.lower()):
        return city if city.startswith("Kota") else "Kota " + city
    if county:
        return county if county.startswith(("Kabupaten", "Kota")) else "Kabupaten " + county
    if city:
        return city if city.startswith(("Kabupaten", "Kota")) else "Kota " + city
    return ""


def lines_for(geo, photon, lat, lon, when, tz):
    a = geo.get("address", {}) or {}
    state = a.get("state") or photon.get("state") or ""
    country = a.get("country") or photon.get("country") or "Indonesia"

    judul = ",".join([p for p in (_region_name(a), state, country) if p])

    kota = a.get("city") or a.get("town")
    parts = []
    for v in (a.get("road") or photon.get("street"),
              a.get("neighbourhood"), a.get("hamlet"), a.get("village"),
              a.get("suburb"), a.get("city_district"),
              kota or a.get("municipality") or photon.get("city"),
              None if kota else _region_name(a),   # kabupaten hanya bila tidak ada nama kota
              state, country):
        if not v:
            continue
        # buang yang cuma beda awalan wilayah, mis. "Pontianak" lalu "Kota Pontianak"
        def norm(t):
            t = t.lower().strip()
            for pre in ("kota ", "kabupaten ", "kecamatan ", "kelurahan ", "desa "):
                if t.startswith(pre):
                    return t[len(pre):]
            return t
        if not any(norm(v) == norm(x) for x in parts):
            parts.append(v)
    alamat = ", ".join(parts) or geo.get("display_name", "") or ""

    koord = f"Lat {lat:.6f}, Long {lon:.6f}"
    waktu = (f"{HARI[when.weekday()]}, {when.day} {BULAN[when.month - 1]} {when.year}, "
             f"{when.strftime('%H:%M')}, GMT{tz:+03d}:00")
    return judul, alamat, koord, waktu


# -------------------------------------------------------------------- panel

def wrap(draw, text, font, max_w):
    words, out, cur = text.split(), [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if draw.textlength(trial, font=font) <= max_w or not cur:
            cur = trial
        else:
            out.append(cur)
            cur = w
    if cur:
        out.append(cur)
    return out


def flag_id(height):
    """Bendera Indonesia kecil, pengganti emoji yang tidak selalu bisa dirender."""
    w = int(height * 1.5)
    f = Image.new("RGBA", (w, height), (0, 0, 0, 0))
    d = ImageDraw.Draw(f)
    d.rectangle([0, 0, w - 1, height // 2 - 1], fill=(206, 17, 38, 255))
    d.rectangle([0, height // 2, w - 1, height - 1], fill=(255, 255, 255, 255))
    d.rectangle([0, 0, w - 1, height - 1], outline=(0, 0, 0, 60), width=1)
    return f


# ---------------------------------------------------------------- exif

def _dms(v):
    v = abs(v)
    d = int(v)
    m = int((v - d) * 60)
    sec = ((v - d) * 60 - m) * 60
    return ((d, 1), (m, 1), (int(round(sec * 10000)), 10000))


def write_exif(path, lat, lon, when, tz, src_exif=None):
    """Timpa GPS dan waktu di file hasil supaya cocok dengan stempel."""
    ex = None
    if src_exif:
        try:
            raw = src_exif
            if not raw.startswith(b"Exif"):
                raw = b"Exif\x00\x00" + raw
            ex = piexif.load(raw)
        except Exception:
            ex = None
    if ex is None:
        ex = {"0th": {}, "Exif": {}, "GPS": {}, "1st": {}, "thumbnail": None}

    for k in ("0th", "Exif", "GPS", "1st"):
        ex.setdefault(k, {})
    ex["thumbnail"] = None

    dt = when.strftime("%Y:%m:%d %H:%M:%S").encode()
    off = ("%+03d:00" % tz).encode()

    ex["GPS"][piexif.GPSIFD.GPSVersionID] = (2, 3, 0, 0)
    ex["GPS"][piexif.GPSIFD.GPSLatitudeRef] = b"S" if lat < 0 else b"N"
    ex["GPS"][piexif.GPSIFD.GPSLatitude] = _dms(lat)
    ex["GPS"][piexif.GPSIFD.GPSLongitudeRef] = b"W" if lon < 0 else b"E"
    ex["GPS"][piexif.GPSIFD.GPSLongitude] = _dms(lon)
    ex["GPS"][piexif.GPSIFD.GPSDateStamp] = when.strftime("%Y:%m:%d").encode()

    ex["Exif"][piexif.ExifIFD.DateTimeOriginal] = dt
    ex["Exif"][piexif.ExifIFD.DateTimeDigitized] = dt
    ex["Exif"][piexif.ExifIFD.OffsetTimeOriginal] = off
    ex["Exif"][piexif.ExifIFD.OffsetTime] = off
    ex["0th"][piexif.ImageIFD.DateTime] = dt

    piexif.insert(piexif.dump(ex), path)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--image", required=True)
    ap.add_argument("--out", required=True)
    ap.add_argument("--lat", type=float, required=True)
    ap.add_argument("--lon", type=float, required=True)
    ap.add_argument("--datetime", dest="dt", required=True, help="YYYY-MM-DD HH:MM (waktu lokal)")
    ap.add_argument("--tz", type=int, default=7)
    ap.add_argument("--zoom", type=int, default=19)
    ap.add_argument("--position", default="bottom-left",
                    choices=["bottom-left", "bottom-right"])
    ap.add_argument("--opacity", type=int, default=150, help="kegelapan panel (0-255)")
    ap.add_argument("--gaya", default="satelit", choices=list(TILES.keys()),
                    help="gaya peta: peta, hijau, satelit, terang")
    args = ap.parse_args()

    when = datetime.strptime(args.dt, "%Y-%m-%d %H:%M")

    photo = ImageOps.exif_transpose(Image.open(args.image)).convert("RGBA")
    W, H = photo.size
    geo, photon = reverse(args.lat, args.lon)
    judul, alamat, koord, waktu = lines_for(geo, photon, args.lat, args.lon, when, args.tz)

    probe = ImageDraw.Draw(Image.new("RGB", (10, 10)))

    def layout(k):
        """Hitung semua ukuran untuk satu skala; dipakai berulang agar pas di foto apa pun."""
        margin, pad, gap = int(20 * k), int(24 * k), int(26 * k)
        map_s = int(300 * k)
        col_w = W - 2 * margin - 2 * pad - map_s - gap
        f_body = ImageFont.truetype(FONT_R, max(13, int(33 * k)))
        flag_w = int(int(48 * k) * 1.5) + int(10 * k)

        best, size = None, max(16, int(48 * k))
        min_one = max(15, int(40 * k))
        while size >= min_one:
            f_try = ImageFont.truetype(FONT_B, size)
            lines = wrap(probe, judul, f_try, col_w - flag_w)
            if len(lines) == 1:
                best = (f_try, lines)
                break
            size -= max(1, int(1 * k))
        if best is not None:
            f_j, jl = best
        else:
            # tidak muat satu baris: tetap tebal dan besar, boleh dua baris
            size = max(15, int(44 * k))
            while size > max(14, int(30 * k)):
                f_j = ImageFont.truetype(FONT_B, size)
                jl = wrap(probe, judul, f_j, col_w - flag_w)
                if len(jl) <= 2:
                    break
                size -= max(1, int(1 * k))

        aw = wrap(probe, alamat, f_body, col_w)
        al = aw[:3]
        if len(aw) > 3 and al:
            al[-1] = al[-1].rstrip(" ,") + "..."

        lh_j = int(f_j.size * 1.25)
        lh_b = int(f_body.size * 1.42)
        text_h = lh_j * len(jl) + lh_b * (len(al) + 2)
        return dict(margin=margin, pad=pad, gap=gap, map_s=map_s, col_w=col_w,
                    f_body=f_body, f_judul=f_j, judul_lines=jl, addr_lines=al,
                    lh_j=lh_j, lh_b=lh_b, text_h=text_h,
                    panel_h=max(map_s, text_h) + 2 * pad)

    # skala mengikuti tinggi foto, jadi panel selalu sekitar seperenam tinggi
    # gambar, baik portrait maupun landscape
    k = H / 2100.0
    k = min(max(k, 0.40), 3.2)
    L = layout(k)
    while L["panel_h"] > 0.30 * H and k > 0.35:      # jangan sampai panel menelan foto
        k *= 0.94
        L = layout(k)

    margin, pad, gap, map_s = L["margin"], L["pad"], L["gap"], L["map_s"]
    col_w = L["col_w"]
    f_body, f_judul = L["f_body"], L["f_judul"]
    judul_lines, addr_lines = L["judul_lines"], L["addr_lines"]
    lh_j, lh_b, text_h = L["lh_j"], L["lh_b"], L["text_h"]
    panel_h = L["panel_h"]

    if args.position == "bottom-right":
        px0 = W - margin - (map_s + gap + col_w + 2 * pad)
    else:
        px0 = margin
    py0 = H - margin - panel_h
    px1, py1 = px0 + map_s + gap + col_w + 2 * pad, py0 + panel_h

    overlay = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(overlay).rounded_rectangle([px0, py0, px1, py1], radius=int(28 * k),
                                             fill=(0, 0, 0, args.opacity))
    photo.alpha_composite(overlay)

    mp, pin_at = map_image(args.lat, args.lon, map_s, args.zoom, args.gaya)
    mp = mp.convert("RGBA")
    draw_pin(mp, pin_at)
    # atribusi wajib OpenStreetMap
    at = ImageDraw.Draw(mp)
    f_at = ImageFont.truetype(FONT_R, max(9, int(13 * k)))
    label = TILES.get(args.gaya, TILES["peta"])[1]
    tw = at.textlength(label, font=f_at)
    at.rectangle([map_s - tw - 6, map_s - f_at.size - 6, map_s, map_s], fill=(0, 0, 0, 110))
    at.text((map_s - tw - 3, map_s - f_at.size - 4), label, font=f_at, fill=(255, 255, 255, 230))

    mask = Image.new("L", (map_s, map_s), 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, map_s - 1, map_s - 1], radius=int(14 * k), fill=255)
    mp.putalpha(mask)
    photo.alpha_composite(mp, (px0 + pad, py0 + pad))

    d = ImageDraw.Draw(photo)
    tx = px0 + pad + map_s + gap
    ty = py0 + pad + max(0, (map_s - text_h) // 2)

    for i, ln in enumerate(judul_lines):
        d.text((tx, ty), ln, font=f_judul, fill=(255, 255, 255, 255))
        if i == len(judul_lines) - 1:
            jw = d.textlength(ln, font=f_judul)
            fl = flag_id(int(f_judul.size * 0.78))
            if tx + jw + int(10 * k) + fl.width < px1 - pad:
                photo.alpha_composite(fl, (int(tx + jw + int(10 * k)),
                                           int(ty + f_judul.size * 0.22)))
        ty += lh_j

    for ln in addr_lines:
        d.text((tx, ty), ln, font=f_body, fill=(255, 255, 255, 240))
        ty += lh_b
    d.text((tx, ty), koord, font=f_body, fill=(255, 255, 255, 240))
    ty += lh_b
    d.text((tx, ty), waktu, font=f_body, fill=(255, 255, 255, 240))

    photo.convert("RGB").save(args.out, "JPEG", quality=92, optimize=True)
    try:
        src_exif = Image.open(args.image).info.get("exif")
    except Exception:
        src_exif = None
    write_exif(args.out, args.lat, args.lon, when, args.tz, src_exif)
    print(f"ok -> {args.out}")
    print(f"   judul : {judul}")
    print(f"   alamat: {alamat}")
    print(f"   koord : {koord}")
    print(f"   waktu : {waktu}")


if __name__ == "__main__":
    main()
