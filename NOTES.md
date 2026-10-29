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

**28-10-2026, Clear always visible.** The Clear button used to appear only after a photo was
chosen, which made it look missing. It now sits under the photo preview from the start,
dimmed until there is something to clear.

**28-10-2026, two different Clears.** I had confused them. The photo Clear now only shows
once a photo is loaded, as before. The search box keeps its round × on the right, appears
as soon as there is text, and Safari's own cancel button is suppressed so there is only
one.

**28-10-2026, reading the photo's own EXIF.** A photo from the library usually already
knows where and when it was taken, so the app now reads that and pre-fills the point and
the date. exifr (MIT, 76 KB) is vendored locally rather than pulled from a CDN so it works
offline and adds no third-party request. Dates are shown in WIB. Camera captures still use
the current moment, and a photo without EXIF simply leaves the defaults alone.

**28-10-2026, pasting coordinates.** The search box now accepts a coordinate pair directly.
Decimal pairs like `-0.347755,109.4402867` and hemisphere forms like `0.347S 109.44E` are
recognised before any lookup is attempted, so nothing is sent to Nominatim for a point we
already have. The order is latitude first, and values outside valid ranges are ignored.

**28-10-2026, create did nothing.** The result card was hidden by the previous change and
never shown again — the stamp was drawn but stayed invisible, which looked exactly like a
failure. Fixed, and two related hazards removed: tile loads now give up after seven
seconds instead of hanging forever, and the button reports which stage it is in while a
large photo is processed.

**28-10-2026, one coordinate format.** The app now speaks a single dialect everywhere:
`Lat -0.347711, Long 109.440144`, six decimals, latitude first. That is what the stamp
prints, what the saved-places list shows, and what tapping the coordinate box copies to
the clipboard. Pasting that same line back into the search box resolves it, because the
parser strips the words and reads the pair.

**28-10-2026, copy button.** The coordinate line got a small clipboard icon on the right,
so copying no longer depends on tapping the whole box. The text is rebuilt from the stored
point, which keeps the format identical to what the stamp prints.

**28-10-2026, clearing saved places.** The list footer now has Clear and Close on one row.
Clear drops every entry that is not starred, so a favourite can only be removed by
unstarring it first — a deliberate guard against wiping a place that matters by accident.

**28-10-2026, map position ignored.** Choosing Right did nothing visible because the map
square was always drawn at the left edge of the panel, even though the panel itself spans
the full width. The map and the text column now swap places when Right is selected.

**28-10-2026, attribution prefix removed.** The Ukrainian flag in the corner came from
Leaflet itself — its default attribution prefix carries it — not from Esri. The prefix is
now empty, so only the tile provider line remains.

**28-10-2026, a second template.** Added Card: the map square sits at the top left with a
thin white edge, and the lower left carries a white time badge in blue, the date in bold,
then the address and the coordinate pair, all running beside a thin yellow rule. Text uses
a soft shadow instead of a panel, so more of the photo stays visible. The template is
picked from a new row on the page; Panel remains the default and is unchanged.

**28-10-2026, Card typography.** The Card template now uses the system font, which is what
the reference image used, instead of the Arial kept for the Panel template. Text is also
constrained to roughly the left half of the frame, so the address wraps into two or three
short lines and the right side of the photo stays clear.

**28-10-2026, Card removed.** The second template did not look right and was deleted at
the owner's request. Notes for the future: the reference app gives each template its own
typeface rather than the system font, so a template is a font and layout pair, not just a
different arrangement.

**29-10-2026, first install feedback.** Three findings from the phone. The IPA placed in the
workspace would not open, but the one from the release link installed fine, so the release
is the path to use. Saving reported success while nothing reached Photos: the page sent the
blob URL instead of image data, so the native side received an unusable string. It now
converts the finished JPEG to base64 and sends that. The home screen icon stayed white
because the source PNG had transparent corners, which iOS renders as blank; the app icon
is flattened onto a solid dark background.

**29-10-2026, Android build.** Added an Android app with the same idea as the iOS one: a
WebView that serves the bundled page through WebViewAssetLoader, so it runs from a real
https origin instead of file:// and avoids the usual local-file restrictions. Two native
bridges exist, one per platform, for saving the finished image straight into the gallery.
The APK is signed with a throwaway key generated during the build, which is enough for
sideloading but means every rebuild is a fresh identity.

**29-10-2026, HEIC without the wait.** Android browsers cannot decode HEIC, which is why
the same page worked on an iPhone and failed on a Galaxy S21. A HEIC converter is now part
of the page and loads only when needed. The first version hung with the spinner running, so
photo loading was rewritten: HEIC is converted before decoding rather than after a failure,
each step has a time limit, and the status line names the step. A photo that genuinely has
no GPS, confirmed in the Samsung gallery, now simply leaves the point empty.

**29-10-2026, the real cause of empty coordinates.** Since Android 10 the system redacts
location tags inside photos from any app that does not hold ACCESS_MEDIA_LOCATION. We never
asked for it, so ExifInterface and the MediaStore latitude column always came back empty —
no amount of re-reading the file could fix that. The manifest now declares
ACCESS_MEDIA_LOCATION and the gallery read permission, both are requested at runtime, and
the app says so on screen ("Foto tidak menyimpan lokasi") instead of failing silently when
a photo genuinely has no location.

**29-10-2026, NaN coordinates.** The Samsung file carries GPS tags filled with zeros, which
decode as NaN rather than null. The old check (`typeof x === "number"`) passes for NaN, so
the app tried to pin a non-existent point and the result looked empty. All three paths —
browser EXIF, Android bridge, and native reader — now share one rule: finite numbers and
not 0,0, otherwise the photo is reported as having no location and the point stays manual.

**29-10-2026, full audit.** Read every file end to end. Findings worth fixing:

1. The service worker deleted the offline tile caches on every update, so "offline maps"
   quietly stopped working after each deploy. Tile caches are now preserved.
2. A slow address lookup could overwrite the address of a newer point. Both the lookup
   and the EXIF reader now carry a sequence token and ignore stale answers.
3. `heic2any.min.js` was never cached, so HEIC conversion failed offline. Added.
4. Android decoded the photo on the UI thread — a big photo could freeze the app (ANR).
   Decoding now runs on a background thread.
5. Android received the finished photo as one multi-megabyte string, past the binder
   limit. The page now sends it in 400 KB chunks and the app reassembles it.
6. Cosmetics: page language set to English, duplicate header-icon and clear-button rules
   removed, dead styles dropped, version marker synced to v42.

Left alone on purpose: the iOS save path (proven working on the phone), the WIB-only
clock (correct for the owner), and the hand-rolled EXIF writer (verified byte-exact
earlier).

**29-10-2026, web cannot read Samsung locations.** A browser has no access to the Gallery
database or the Google account, so a Samsung photo whose file carries no GPS will never
autofill on the web — that is a platform limit, not a bug. The Android app covers it
through the MediaStore fallback. For the web, a "Use current location" button now appears
whenever the photo has no location, filling the point from the phone's GPS in one tap.

**29-10-2026, IPA frozen.** The installed IPA shows a blank map while the same page works
in Safari, and the owner asked that the IPA no longer be touched. The iOS workflow is
disabled (renamed to `ios.yml.disabled`), so future web or Android work cannot rebuild or
replace it. The release IPA stays exactly as it is. Suspected cause, not acted on: the app
bundles the page but still fetches map tiles from the internet, so a blank map in the
field most likely means no signal at that moment — needs confirmation from the phone.

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
