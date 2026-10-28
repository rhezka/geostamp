// Service worker sederhana: simpan berkas sendiri agar cepat dibuka dan tetap jalan offline.
// Permintaan ke peta dan pencarian alamat selalu lewat jaringan.
const CACHE = "geostamp-v30";
const TILE_CACHE = "geostamp-tiles";
const TILE_HOSTS = ["tile.openstreetmap.org", "server.arcgisonline.com", "a.tile.opentopomap.org", "a.basemaps.cartocdn.com"];
const ASSETS = ["./", "./index.html", "./manifest.json", "./icon-192.png", "./icon-512.png", "./icon-180.png", "./exifr.min.js"];
const TILE_CACHE_CORS = "geostamp-tiles-cors";

self.addEventListener("install", (e) => {
  e.waitUntil(caches.open(CACHE).then((c) => c.addAll(ASSETS)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches.keys().then((keys) => Promise.all(keys.filter((k) => k !== CACHE).map((k) => caches.delete(k))))
      .then(() => self.clients.claim())
  );
});

self.addEventListener("fetch", (e) => {
  const url = new URL(e.request.url);

  // halaman: selalu ambil versi terbaru dari jaringan, cache hanya untuk keadaan offline
  if (e.request.mode === "navigate" || (url.origin === location.origin && url.pathname.endsWith("/"))) {
    e.respondWith((async () => {
      try {
        const fresh = await fetch(e.request, { cache: "no-store" });
        const c = await caches.open(CACHE);
        c.put(e.request, fresh.clone()).catch(() => {});
        return fresh;
      } catch (err) {
        const hit = await caches.match(e.request);
        return hit || caches.match("./index.html");
      }
    })());
    return;
  }

  // tile peta: pisahkan cache untuk permintaan CORS (dipakai kanvas) dan opaque
  // (dipakai peta Leaflet). Mencampur keduanya membuat kanvas jadi tercemar.
  if (TILE_HOSTS.includes(url.hostname)) {
    const cors = e.request.mode === "cors";
    const name = cors ? TILE_CACHE + "-cors" : TILE_CACHE + "-opaque";
    e.respondWith((async () => {
      const cache = await caches.open(name);
      const hit = await cache.match(e.request.url);
      if (hit) return hit;
      try {
        const res = await fetch(e.request);
        if (res && res.ok) cache.put(e.request.url, res.clone()).catch(() => {});
        return res;
      } catch (err) {
        return new Response("", { status: 504 });
      }
    })());
    return;
  }

  if (url.origin !== location.origin) return;          // geocode dsb: langsung jaringan
  if (e.request.method !== "GET") return;
  e.respondWith(
    caches.match(e.request).then((hit) => hit || fetch(e.request).then((res) => {
      const copy = res.clone();
      caches.open(CACHE).then((c) => c.put(e.request, copy)).catch(() => {});
      return res;
    }).catch(() => caches.match("./index.html")))
  );
});
