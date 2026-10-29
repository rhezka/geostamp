package id.rhezka.geostamp

import android.Manifest
import android.content.ContentValues
import android.content.Intent
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

class MainActivity : androidx.activity.ComponentActivity() {

    private lateinit var web: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val pickImage = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val prepared = uri?.let { prepareImage(it) }
        fileCallback?.onReceiveValue(prepared?.let { arrayOf(it) })
        fileCallback = null
    }

    /**
     * Foto dari galeri disalin dulu jadi JPEG di cache. Ini perlu karena WebView tidak
     * selalu bisa membaca HEIC atau URI penyimpanan awan langsung.
     */
    private fun prepareImage(source: Uri): Uri? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(source).use { BitmapFactory.decodeStream(it, null, bounds) }

            val longest = maxOf(bounds.outWidth, bounds.outHeight)
            var sample = 1
            while (longest / sample > 4096) sample *= 2

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = contentResolver.openInputStream(source).use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            val rotation = contentResolver.openInputStream(source).use { input ->
                when (ExifInterface(input).getAttributeInt(ExifInterface.TAG_ORIENTATION, 1)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }

            val fixed = if (rotation != 0f) {
                val m = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, m, true)
            } else bitmap

            val out = File(cacheDir, "picked.jpg")
            out.outputStream().use { fixed.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            Uri.fromFile(out)
        } catch (e: Exception) {
            null
        }
    }

    private val takePhoto = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { ok: Boolean ->
        val uri = cameraUri
        fileCallback?.onReceiveValue(if (ok && uri != null) arrayOf(uri) else null)
        fileCallback = null
        cameraUri = null
    }

    private var cameraUri: Uri? = null

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

        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.CAMERA
            ), 1
        )
    }

    private fun newImageUri(): Uri? {
        val name = "geostamp-" + System.currentTimeMillis() + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }


    // jembatan: halaman mengirim gambar jadi (base64) untuk disimpan ke galeri
    inner class Bridge {
        @JavascriptInterface
        fun saveImage(base64: String) {
            try {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
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
        }
    }
}
