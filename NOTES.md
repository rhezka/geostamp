# Notes

Working log for GeoStamp, newest entries at the bottom of each section. Times are WIB
(UTC+7).

## How it started

The first version was built inside the Assistant workspace, as a way to get around an
App Store app that demanded iOS 17.6 while the phone was stuck on 17.0. Stacking an
overlay ourselves turned out to be simpler than fighting the version gate, and the result
looked close enough to the app that inspired it.

## Decisions we settled on

The stamp sits in a dark translucent panel, alpha 150 out of 255, with the map square on
the left, a bold region line, then the address, the coordinates, and finally the day,
date, time and GMT offset. The Indonesian flag is drawn as two small rectangles rather
than an emoji, because the emoji does not render reliably on every platform.

Size follows the photo's height, `k = height / 2100`, and the panel is never allowed to
take more than thirty percent of the frame. In the web page that base size is multiplied
by 1.25, which is what the slider calls one hundred percent.

The default map is satellite imagery at zoom 19, with automatic fallback when tiles are
missing. Arial is used everywhere, matching Liberation Sans on the server so both paths
produce the same output.

Every result has its EXIF rewritten: GPS latitude and longitude, DateTimeOriginal,
DateTimeDigitized, and the plus seven offset.

## Change log

**28-10-2026.** First build, then dozens of small corrections. Size originally keyed off
width, which made the panel enormous on landscape photos; switched to height. The
browser's system font made headings grow on their own; switched to Arial. Satellite tiles
came back blank around Sanggau; added a variance check and a fallback chain. Address lines
lost the regency because city and village were mixed up; fixed. Downloads landed in Files
instead of Photos; replaced with the share sheet.

The project moved out of the Assistant workspace into this repository. The GitHub name
changed from `geotag-editor` to `geostamp`, and the local folder followed. One stale entry
remains in the Manager's repository list because its API has no delete call.

The interface was rewritten in English, then trimmed: numbered labels out, stray hints
out, native file inputs hidden so no "Choose File" text appears. Header gained a gradient,
the icon moved to the left, the title grew, and "by rhezka" sits underneath.

Map Style and Map Position became two side-by-side boxes, each with a small label and the
current value, opening the same bottom sheet as Add Photo. Zoom controls moved to the
bottom right after they overlapped the sticky header.

Locations are remembered, but only when a stamp is actually created — pin taps and
searches no longer pollute the list. Fifteen recent points are kept, favourites stay
forever. Storage is `localStorage`, deliberately separate from the cache that holds tiles.

Offline support arrived last. The service worker keeps two separate tile caches: one for
CORS requests (used by the canvas) and one for opaque requests (used by Leaflet). Mixing
them taints the canvas and breaks saving, which is why the split exists.

A hit counter was added through hits.sh. It counts page openings, not unique visitors;
the number is visible at https://hits.sh/rhezka.github.io/geostamp/

**28-10-2026, satellite gaps.** Some photos came out with an empty map box while the
street map worked. Measuring tiles by hand explained it: around Sanggau the zoom 19 Esri
tile scores 3.6 on colour variance, a grey placeholder, while zoom 18 scores 36.7. Real
imagery sits between 17 and 41. So the app now rejects any tile below 8, loads at most six
at a time with one retry, and steps down through 19, 18 and 17 before falling back to the
street map. A map is only accepted when at least eighty-five percent of its tiles are real.

The repository description on GitHub was still Indonesian; changed to English, homepage
and topics added.

**28-10-2026, water mistaken for holes.** A photo from Jungkat, Mempawah, came back with
grey blocks in the map box. The cause was the tile filter itself: a sea tile is almost
perfectly uniform, so it looked like a placeholder. Measuring both settled it — a missing
Esri tile averages 205 brightness with a spread of 3.6, while water averages 33 with a
spread of 0. The filter now only discards tiles that are both very flat and very light.
Street maps keep a higher score threshold than satellite, since a patch of open sea is a
legitimate, featureless image.

**28-10-2026, zoom choice.** Ship inspections often happen at sea or at Jungkat, where
high zoom satellite is patchy. Added a Map Zoom row with three settings: Close tries 19
then 18, Auto tries 19, 18 and 17, and Wide starts at 17 and can drop to 15 so the
coastline stays in frame.

**28-10-2026, still grey boxes.** The Jungkat photo still had light rectangles. Checking
the tiles by hand showed why: at zoom 19 every tile is a flat placeholder (mean 205), and
at zoom 18 the placeholders average 145–155 — close enough to a sandbar that a brightness
test cannot separate them.

Two fixes. The service worker now fetches the page from the network first, so a new build
reaches the phone on the next open instead of being served from cache indefinitely; that
alone explained most of the "still broken" reports. And a satellite map is only accepted
when every tile is real, otherwise the app steps down a zoom or switches to the street map.

**28-10-2026, search clear.** Added a round × button inside the search field. It only
appears while there is text, empties the box and closes the result list, and leaves the
cursor ready for a new query.

**28-10-2026, quieter empty state.** The line "Tap the map to set a point" is gone, and the
coordinate box now stays hidden until a point exists. The result card is also hidden until
the first stamp is made, so the page ends cleanly instead of showing an empty panel.

## Where things live

`index.html` holds the whole app. `tools/stempel_foto.py` is the batch version.
`tools/exif-writer.js` is the EXIF writer that also gets inlined into the page.
`manifest.json` and `sw.js` make it installable and cacheable. Icons come in five sizes.

## Rolling back

Every change is a commit. To return to an earlier state, check out that commit and push.
The site rebuilds from `main` within a minute.

## Quick check

```bash
.venv/bin/python tools/stempel_foto.py --image contoh-foto-uji.jpg --out /tmp/uji.jpg \
  --lat -0.019964 --lon 109.337110 --datetime "2026-10-28 08:11"

.venv/bin/python -c "import piexif;print(piexif.load('/tmp/uji.jpg')['GPS'])"
```
