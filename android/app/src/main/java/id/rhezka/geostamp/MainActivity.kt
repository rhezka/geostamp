package id.rhezka.geostamp

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.exifinterface.media.ExifInterface
import androidx.webkit.WebViewAssetLoader
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MainActivity : androidx.activity.ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var cameraUri: Uri? = null

    private data class Picked(
        val file: File?,
        val lat: Double?,
        val lon: Double?,
        val millis: Long?
    )

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? -> handlePicked(uri, fromCamera = false) }

    private val takePhoto = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        val target = cameraUri
        cameraUri = null
        handlePicked(if (ok) target else null, fromCamera = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        web = WebView(this)
        web.setBackgroundColor(Color.parseColor("#0F1115"))
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }

        web.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                callback.invoke(origin, true, false)
            }

            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                if (params.isCaptureEnabled) {
                    val target = newImageUri()
                    cameraUri = target
                    if (target != null) {
                        takePhoto.launch(target)
                        return true
                    }
                }
                pickImage.launch("image/*")
                return true
            }
        }

        web.addJavascriptInterface(Bridge(), "AndroidBridge")
        setContentView(web)
        web.loadUrl("https://appassets.androidplatform.net/assets/www/index.html")

        mintaIzin()
    }

    /** Izin dibaca + lokasi media wajib ada sebelum EXIF foto dibaca. */
    private fun mintaIzin() {
        val daftar = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            daftar.add(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            daftar.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        // ACCESS_MEDIA_LOCATION ada sejak Android 10 (API 29)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            daftar.add(Manifest.permission.ACCESS_MEDIA_LOCATION)
        }
        val kurang = daftar.filter {
            checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
        }
        if (kurang.isNotEmpty()) requestPermissions(kurang.toTypedArray(), 1)
    }

    private fun handlePicked(uri: Uri?, fromCamera: Boolean) {
        if (uri == null) {
            fileCallback?.onReceiveValue(null)
            fileCallback = null
            return
        }
        // Decode bitmap di thread latar: di thread utama bisa macet (ANR) untuk foto besar
        Thread {
            val picked = prepare(uri)
            runOnUiThread {
                fileCallback?.onReceiveValue(picked?.file?.let { arrayOf(Uri.fromFile(it)) })
                fileCallback = null
                if (picked?.file != null) {
                    kirimMeta(picked.lat, picked.lon, picked.millis)
                    // Jangan diam kalau lokasi tidak terbaca dari foto galeri
                    if (!fromCamera && picked.lat == null) {
                        Toast.makeText(
                            this@MainActivity,
                            "Foto tidak menyimpan lokasi — titik diisi manual",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }.start()
    }

    /**
     * Foto disalin dulu ke berkas sementara, baru dibaca. Dengan begitu metadata bisa
     * dibaca andal, dan hasilnya diubah jadi JPEG supaya WebView pasti bisa membukanya.
     */
    private fun prepare(source: Uri): Picked {
        val temp = File(cacheDir, "source.tmp")
        var lat: Double? = null
        var lon: Double? = null
        var millis: Long? = null

        try {
            contentResolver.openInputStream(source)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return Picked(null, null, null, null)
        } catch (e: Exception) {
            return Picked(null, null, null, null)
        }

        try {
            val exif = ExifInterface(temp.absolutePath)
            exif.latLong?.let {
                // Tag GPS berisi nol semua menghasilkan NaN — tolak agar tidak dikirim
                if (it[0].isFinite() && it[1].isFinite()
                    && !(it[0] == 0.0 && it[1] == 0.0)) {
                    lat = it[0]
                    lon = it[1]
                }
            }
            val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            if (raw != null) {
                val offset = exif.getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL)
                val fmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US)
                fmt.timeZone = if (offset != null) TimeZone.getTimeZone("GMT$offset")
                               else TimeZone.getDefault()
                millis = fmt.parse(raw)?.time
            }
        } catch (e: Exception) {
            // tanpa metadata tetap lanjut
        }

        // Sebagian foto Samsung menyimpan lokasi di basis data Galeri, bukan di dalam
        // berkasnya. Kalau EXIF kosong, coba ambil dari MediaStore.
        if (lat == null) {
            try {
                contentResolver.query(
                    source,
                    arrayOf(
                        MediaStore.Images.Media.LATITUDE,
                        MediaStore.Images.Media.LONGITUDE,
                        MediaStore.Images.Media.DATE_TAKEN
                    ), null, null, null
                )?.use { c ->
                    if (c.moveToFirst()) {
                        if (!c.isNull(0) && !c.isNull(1)) {
                            val la = c.getDouble(0)
                            val lo = c.getDouble(1)
                            if (!(la == 0.0 && lo == 0.0)) { lat = la; lon = lo }
                        }
                        if (millis == null && !c.isNull(2)) millis = c.getLong(2)
                    }
                }
            } catch (e: Exception) { /* abaikan */ }
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)

            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > 4096) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeFile(temp.absolutePath, opts)
                ?: return Picked(null, lat, lon, millis)

            val rotation = try {
                when (ExifInterface(temp.absolutePath)
                    .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } catch (e: Exception) { 0f }

            val fixed = if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            } else bitmap

            val out = File(cacheDir, "picked.jpg")
            out.outputStream().use { fixed.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Picked(out, lat, lon, millis)
        } catch (e: Exception) {
            Picked(null, lat, lon, millis)
        }
    }

    private fun kirimMeta(lat: Double?, lon: Double?, millis: Long?) {
        val la = lat?.toString() ?: "null"
        val lo = lon?.toString() ?: "null"
        val ms = millis?.toString() ?: "null"
        web.post {
            web.evaluateJavascript("window.geostampApplyExif($la, $lo, $ms)", null)
        }
    }

    private fun newImageUri(): Uri? {
        val name = "geostamp-" + System.currentTimeMillis() + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    inner class Bridge {
        private val saveBuffer = StringBuilder()

        @JavascriptInterface
        fun saveImage(base64: String) {
            saveBuffer.clear()
            saveBuffer.append(base64)
            simpanBuffer()
        }

        /** Halaman mengirim base64 per potongan karena binder tak tahan string multi-MB. */
        @JavascriptInterface
        fun saveStart(total: Int) {
            saveBuffer.clear()
            saveBuffer.ensureCapacity(total)
        }

        @JavascriptInterface
        fun saveChunk(part: String) {
            saveBuffer.append(part)
        }

        @JavascriptInterface
        fun saveEnd() {
            simpanBuffer()
        }

        private fun simpanBuffer() {
            val b64 = saveBuffer.toString()
            saveBuffer.clear()
            Thread {
                try {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val name = "geostamp-" + System.currentTimeMillis() + ".jpg"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeoStamp")
                        }
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                    uri?.let {
                        contentResolver.openOutputStream(it)?.use { out -> out.write(bytes) }
                    }
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Saved to gallery", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Could not save", Toast.LENGTH_SHORT).show()
                    }
                }
            }.start()
        }
    }
}
