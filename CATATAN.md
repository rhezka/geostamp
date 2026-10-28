# Catatan Pengembangan

## 28-10-2026 — repo dibuat, alat dipindahkan dari workspace Assistant

Isi awal: `index.html` (aplikasi satu halaman), `tools/stempel_foto.py` (versi CLI),
`tools/exif-writer.js` (penulis EXIF untuk jalur browser), `contoh-foto-uji.jpg`.

Asal: dikembangkan di workspace Assistant (`/workspace/repos/assistant`), lalu dipindahkan
ke repo ini atas permintaan pemilik.

### Keputusan yang sudah disepakati

- Bentuk stempel mengikuti contoh pemilik: panel gelap semi transparan (alpha 150/255),
  peta kotak dengan pin merah di kiri, judul wilayah tebal, alamat, koordinat, lalu
  hari/tanggal/jam dengan GMT+07:00 dan bendera Indonesia.
- Ukuran mengikuti tinggi foto: `k = tinggi / 2100`, dijaga agar panel tidak melebihi
  30% tinggi gambar. Di halaman, `k` dikali 1,25 sebagai ukuran baku 100%.
- Gaya peta bawaan: citra satelit zoom 19, dengan mundur otomatis bila tile kosong.
- Font: Arial (jalur server memakai Liberation Sans, metriknya sama).
- EXIF hasil selalu ditimpa: GPS, DateTimeOriginal, DateTimeDigitized, OffsetTime (+07:00).

### Perbaikan yang pernah dilakukan (riwayat singkat)

1. Ukuran awal memakai lebar foto, membuat panel terlalu besar di foto mendatar.
   Diganti memakai tinggi foto.
2. Huruf bawaan Apple di browser membuat judul membesar sendiri. Diganti Arial agar
   sama dengan versi server.
3. Citra satelit kosong di kawasan tanpa liputan (mis. Sanggau). Ditambah deteksi
   keragaman warna dan rantai cadangan zoom lalu peta standar.
4. Alamat kehilangan kabupaten/kecamatan karena logika kota/desa tercampur. Diperbaiki.
5. Tombol simpan memakai unduhan biasa sehingga masuk ke Files. Diganti `navigator.share`
   agar muncul pilihan Simpan Gambar.

### Publikasi

- 28-10-2026: repo dijadikan publik dan GitHub Pages diaktifkan dari branch `main`, folder akar.
  Alamat: https://rhezka.github.io/geotag-editor/ (status build: built, HTTP 200).

### 28-10-2026 — tampilan bahasa Inggris

Seluruh teks halaman diganti ke bahasa Inggris dan label bernomor dihapus. Tidak ada
perubahan logika. Foto tetap diproses di browser, tidak pernah diunggah.

### 28-10-2026 — merek, ikon, kamera, PWA

- Nama merek: **PinStamp**. Ikon dibuat sendiri (pin + baris keterangan) dalam ukuran
  1024, 512, 192, 180, dan 32.
- Tambah tombol **Camera** di samping **Gallery**. Foto dari kamera otomatis memakai waktu
  saat itu dan meminta izin GPS untuk menaruh titik. Foto dari galeri tetap manual.
- Tambah `manifest.json` dan `sw.js` supaya bisa dipasang ke layar utama (PWA) dan
  tetap terbuka saat offline. Hanya berkas sendiri yang di-cache; peta dan pencarian
  alamat selalu lewat jaringan.
- Catatan: ini PWA, bukan UWP. Aplikasi Windows (UWP/MSIX) jalur terpisah.

### 28-10-2026 — ganti merek ke GeoStamp

- Merek diganti dari PinStamp menjadi **GeoStamp** atas permintaan pemilik.
- Nama repo GitHub diganti `geotag-editor` menjadi `geostamp`; alamat halaman pindah ke
  https://rhezka.github.io/geostamp/ . Nama folder lokal masih `geotag-editor` karena
  terdaftar di OpenCode Manager.
- Catatan pasar: nama GeoStamp sudah dipakai banyak aplikasi lain (App Store, Play Store).
  Aman untuk pemakaian pribadi, tapi kalau nanti mau dipublikasikan resmi sebaiknya
  dibuat nama yang lebih khas.

### 28-10-2026 — satu tombol foto, tombol Clear, repo pindah

- Tombol Gallery dan Camera digabung menjadi satu tombol **Add Photo**; pilihan
  Camera / Choose from Library muncul sebagai lembar bawah setelah diketuk.
- Tambah tombol **Clear** untuk mengosongkan foto, titik, dan hasil sekaligus.
- Repo lokal dipindah ke `/workspace/repos/geostamp` (id 14 di Manager). Folder lama
  `geotag-editor` dihapus. Entri repo lama (id 13) masih tersisa di daftar Manager
  karena tidak ada API untuk menghapus; bisa dibersihkan dari halaman Repos di Manager.
- Situs tetap di https://rhezka.github.io/geostamp/ .

### 28-10-2026 — slider 50-150, tile disimpan untuk offline

- Slider ukuran sekarang 50 sampai 150 persen, dengan 100 persen tepat di tengah.
- Service worker menyimpan tile peta. Cache dipisah dua: `geostamp-tiles-cors` untuk
  permintaan ber-CORS (dipakai kanvas saat membuat stempel) dan `geostamp-tiles-opaque`
  untuk peta Leaflet. Kalau dicampur, kanvas jadi tercemar dan penyimpanan gambar gagal.
- Saat titik dipasang dan perangkat online, 5x5 tile di sekitar titik disimpan untuk
  zoom 19, 18, dan 17. Jadi titik yang pernah dibuka masih punya peta saat offline.
  Titik baru yang belum pernah dibuka tetap tidak bisa dirender tanpa internet.

### 28-10-2026 — header berikon, pilihan gaya peta berupa menu

- Header diberi jarak aman dari status bar (`env(safe-area-inset-top)`), ditambah ikon
  aplikasi dan judul di tengah.
- Kolom bawaan "Choose File / no file selected" hilang: semua `input[type=file]`
  disembunyikan, termasuk input kamera.
- Pilihan Satellite dan Map left diganti dua baris menu: **Map Style** dan
  **Map Position**, dengan nilai aktif terlihat di kanan. Mengetuk membuka lembar
  pilihan dari bawah, sama seperti Add Photo.

### 28-10-2026 — kredit "by rhezka"

Ditambahkan baris kecil "by rhezka" di bawah judul aplikasi. Sengaja tidak dimasukkan ke
dalam stempel foto, karena stempel dipakai untuk bukti kerja; kredit penulis di gambar
justru bisa mengganggu. Kalau nanti ingin muncul di gambar, bisa ditambah opsi terpisah.

### 28-10-2026 — header beridentitas, pilihan peta dua kotak

- Header diberi latar gradasi gelap dan garis bawah, ikon 46 px di kiri, judul 26 px,
  dan "by rhezka" di bawah judul dengan warna lebih terang agar tidak samar.
- Map Style dan Map Position kembali sejajar satu baris berisi dua kotak. Tiap kotak
  dua baris: label kecil di atas, nilai aktif di bawah (Satellite/Street, Left/Right).
- Teks nilai dipendekkan: "Street" dan "Left"/"Right" tanpa kata Map.

### Rollback

Repo masih baru dan mandiri. Untuk kembali ke keadaan sebelum pemindahan, berkas asli
masih ada di workspace Assistant (`stempel-foto.html` dan `tools/stempel_foto.py`)
sampai dibersihkan.

### Cara uji cepat

```bash
.venv/bin/python tools/stempel_foto.py --image contoh-foto-uji.jpg --out /tmp/uji.jpg \
  --lat -0.019964 --lon 109.337110 --datetime "2026-10-28 08:11"
```

Lalu periksa EXIF:

```bash
.venv/bin/python -c "import piexif;print(piexif.load('/tmp/uji.jpg')['GPS'])"
```
