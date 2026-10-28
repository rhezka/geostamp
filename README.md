# GeoStamp

A small web app that turns an ordinary photo into one carrying a map thumbnail, the
address, the coordinates, and the time it was taken. It also writes the same location and
time into the file's EXIF, so the picture and its metadata always agree.

Open it at **https://rhezka.github.io/geostamp/**

On an iPhone, open that address in Safari and use Share → Add to Home Screen. It then
behaves like an app: full screen, its own icon, and it still opens when the signal drops.

## What it does

You pick a photo, either from the library or straight from the camera. From the camera,
the app fills in the current time and asks for your position so the point matches where
you are standing. From the library, you set the point yourself.

A search box finds a place by name, or you can simply tap the map to drop a pin and drag
it until it sits exactly where you want. The address, the map thumbnail, and the numbers
all come from that one point, so nothing can drift out of sync.

Before saving you can adjust the date and time, choose between satellite imagery and a
plain street map, decide whether the map box sits on the left or the right, and scale the
whole stamp up or down. A hundred percent is the agreed default.

The result appears at the bottom of the page. Tap Save to Photos to send it through the
iOS share sheet, or press and hold the image and choose Save to Photos.

## Places you have used

Every point that actually gets stamped is remembered on the device, along with a name.
The last fifteen stay in the list, and any of them can be starred so it stays at the top.
They are stored in the browser's local storage, not on any server, and they survive
reopening the app.

## Offline behaviour

The page itself is cached, so it opens without a network. Map tiles are another matter:
they have to be downloaded at some point. Whenever you set a point while online, the app
quietly keeps the surrounding tiles. Return to that same point later without a signal and
the map still appears. A brand new point, never opened before, will show an empty box
until you are back online. Address lookup also needs a connection; the coordinates and
time are written either way.

## Where the map comes from

The street map is OpenStreetMap, the satellite imagery is Esri World Imagery with Maxar,
and the address lookup uses Nominatim. All three require credit, so a small attribution
line is drawn on the map thumbnail. Please leave it there.

Google Maps is deliberately not used: it needs a paid API key and its imagery may not be
redrawn into a picture.

## Running it yourself

Everything is static. Serve the folder with any web server, or open `index.html`
directly. There is no build step.

The page keeps the whole stamping routine in JavaScript, including a small EXIF writer
(`tools/exif-writer.js`, also inlined in the page) that inserts the GPS block and the
date/time tags into the JPEG after the canvas is drawn.

There is a command line version for batches:

```bash
uv venv .venv
uv pip install --python .venv/bin/python pillow pillow-heif piexif

.venv/bin/python tools/stempel_foto.py \
  --image photo.heic --out result.jpg \
  --lat -0.019964 --lon 109.337110 \
  --datetime "2026-10-28 08:11"
```

Useful flags: `--gaya satelit|peta|hijau|terang`, `--zoom 19`,
`--position bottom-left|bottom-right`, `--opacity 150`, `--tz 7`.

## Known limits

The address is only as complete as OpenStreetMap's data. Small alleys are often missing,
and in some places only the village or district is known.

Satellite coverage is patchy in rural areas. The page notices empty tiles and falls back
to another zoom level, then to the street map.

iOS caps canvas size at roughly sixteen million pixels, so very large photos are scaled
down before drawing.

The browser path writes GPS and time into EXIF but not the camera make and model, because
browsers cannot read that from a HEIC file. The command line version keeps the original
camera tags.

## Visits

A tiny counter records page openings. It counts openings, not distinct people, so treat
the number as a rough signal rather than an audience measurement. The figure is public at
https://hits.sh/rhezka.github.io/geostamp/

## Credit

Built by rhezka.
