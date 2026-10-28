# GeoStamp

Stempel foto: peta kecil, alamat, koordinat, dan waktu, dengan EXIF yang ikut ditulis.

Alamat online: https://rhezka.github.io/geostamp/

Dipasang di iPhone: buka alamat itu di Safari, lalu Bagikan > Tambahkan ke Layar Utama.

Alat untuk mengubah foto polos menjadi foto berstempel: peta kecil, alamat, koordinat,
dan waktu — sekaligus menulis koordinat dan waktu itu ke dalam EXIF berkas hasil.

Ada dua bentuk:

- `index.html` — aplikasi satu halaman, jalan di browser HP. Tidak butuh server.
- `tools/stempel_foto.py` — versi baris perintah, untuk memproses banyak foto sekaligus.

## Cara pakai halaman (index.html)

1. Buka `index.html` di Safari (atau browser lain).
2. Pilih foto dari galeri. Format HEIC dari iPhone didukung Safari.
3. Cari tempat di kotak pencarian, atau ketuk peta untuk menaruh pin. Pin bisa digeser.
4. Isi tanggal dan jam (bawaan: waktu sekarang, WIB).
5. Atur ukuran stempel bila perlu. Angka 100% adalah ukuran baku.
6. Ketuk **Buat stempel**. Hasil muncul di bawah, beserta keterangan nilai EXIF yang ditulis.
7. Ketuk **Simpan ke Foto** (membuka menu Bagikan → Simpan Gambar), atau tekan lama
   pada gambar hasil.

Catatan: kalau dibuka di dalam panel chat Manager, penyimpanan bisa terbatas dan peta
bisa gagal dimuat. Untuk pemakaian sebenarnya, buka di Safari.

## Cara pakai versi baris perintah

```bash
uv venv .venv
uv pip install --python .venv/bin/python pillow pillow-heif piexif requests

.venv/bin/python tools/stempel_foto.py \
  --image foto.heic --out hasil.jpg \
  --lat -0.019964 --lon 109.337110 \
  --datetime "2026-10-28 08:11"
```

Opsi lain: `--gaya satelit|peta|hijau|terang`, `--zoom 19`, `--position bottom-left|bottom-right`,
`--opacity 150`, `--tz 7`.

Keluarannya JPEG dengan EXIF GPS dan waktu yang sudah disamakan dengan stempel.
Data kamera asli (merek dan model) tetap terbawa pada jalur ini.

## Sumber peta dan aturan atribusi

- Peta standar: OpenStreetMap, wajib tulis "© OpenStreetMap".
- Citra satelit: Esri World Imagery / Maxar, wajib tulis "© Esri, Maxar, Earthstar Geographics".
- Alamat: Nominatim (OpenStreetMap).

Atribusi sudah dicetak kecil di pojok peta. Jangan dihapus.

Google Maps tidak dipakai karena memerlukan API berbayar dan citranya tidak boleh
dicetak ulang ke dalam gambar.

## Batasan yang perlu diketahui

- Kelengkapan alamat bergantung data OpenStreetMap. Gang kecil bisa belum terdaftar.
- Citra satelit tidak tersedia di semua lokasi. Halaman otomatis mundur ke zoom lain
  lalu ke peta standar.
- iOS membatasi ukuran kanvas sekitar 16 juta piksel. Foto yang lebih besar diperkecil
  dulu sebelum digambar.
- Jalur browser hanya menulis GPS dan waktu ke EXIF, bukan merek dan model kamera.
