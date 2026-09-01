package `in`.scanplay.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.File
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {
    private lateinit var web: WebView
    private lateinit var swipe: SwipeRefreshLayout
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermission: PermissionRequest? = null
    private val home = "https://scanplay.in/?app=1"

    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uris = WebChromeClient.FileChooserParams.parseResult(r.resultCode, r.data)
        fileCallback?.onReceiveValue(uris); fileCallback = null
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingPermission?.let { if (granted) it.grant(it.resources) else it.deny() }; pendingPermission = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        web = findViewById(R.id.web); swipe = findViewById(R.id.swipe)
        // Android 15 draws edge-to-edge: keep web content clear of the status bar and navigation bar
        ViewCompat.setOnApplyWindowInsetsListener(swipe) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        swipe.setBackgroundColor(0xFFFFFFFF.toInt())
        swipe.setColorSchemeColors(0xFF7C3AED.toInt(), 0xFFFF4D6D.toInt())
        swipe.setOnRefreshListener { web.reload() }

        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false          // AR player autoplays video
            allowFileAccess = true; loadWithOverviewMode = true; useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString + " ScanPlayApp/1.0"
            javaScriptCanOpenWindowsAutomatically = true; setSupportMultipleWindows(true)
        }
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        WebView.setWebContentsDebuggingEnabled(false)
        web.addJavascriptInterface(object {
            @JavascriptInterface fun setPullRefresh(on: Boolean) { runOnUiThread { swipe.isEnabled = on && pullAllowed(web.url ?: "") } }
            @JavascriptInterface fun shareImage(b64: String, text: String) {
                try {
                    val dir = File(cacheDir, "share").apply { mkdirs() }
                    val f = File(dir, "scanplay-share.jpg"); f.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                    val uri = FileProvider.getUriForFile(this@MainActivity, "$packageName.fileprovider", f)
                    val i = Intent(Intent.ACTION_SEND).apply { type = "image/jpeg"; putExtra(Intent.EXTRA_STREAM, uri); putExtra(Intent.EXTRA_TEXT, text); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                    runOnUiThread { startActivity(Intent.createChooser(i, "Share")) }
                } catch (e: Exception) { runOnUiThread { web.evaluateJavascript("window.open('https://wa.me/?text='+encodeURIComponent(${org.json.JSONObject.quote(text)}))", null) } }
            }
        }, "ScanPlayApp")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url; val scheme = u.scheme ?: ""; val host = u.host ?: ""
                // App schemes (UPI apps, WhatsApp, tel, mail, intent) -> hand to the OS
                if (scheme !in listOf("http", "https")) {
                    try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (e: Exception) {
                        if (scheme == "intent") { try { val i = Intent.parseUri(u.toString(), Intent.URI_INTENT_SCHEME); val fb = i.getStringExtra("browser_fallback_url"); if (fb != null) view.loadUrl(fb) else startActivity(i) } catch (_: Exception) {} }
                    }
                    return true
                }
                // WhatsApp / Play Store / Drive / Maps -> external apps
                val external = listOf("wa.me", "api.whatsapp.com", "web.whatsapp.com", "play.google.com", "drive.google.com", "maps.google.com")
                if (external.any { host == it || host.endsWith(".$it") }) { startActivity(Intent(Intent.ACTION_VIEW, u)); return true }
                // Everything else (scanplay.in, Razorpay, bank net-banking pages, YouTube embeds) stays inside the WebView
                return false
            }
            override fun onPageFinished(view: WebView, url: String) { swipe.isRefreshing = false; swipe.isEnabled = pullAllowed(url) }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {   // camera for AR
                if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(request.resources)
                    else { pendingPermission = request; cameraPermission.launch(Manifest.permission.CAMERA) }
                } else request.deny()
            }
            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                // Razorpay / bank pages that open a new window: load them in the same WebView
                val transport = resultMsg.obj as WebView.WebViewTransport
                val tmp = WebView(this@MainActivity); tmp.webViewClient = object : WebViewClient() { override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { web.loadUrl(r.url.toString()); return true } }
                transport.webView = tmp; resultMsg.sendToTarget(); return true
            }
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {   // photo/video uploads
                fileCallback?.onReceiveValue(null); fileCallback = cb
                return try { filePicker.launch(p.createIntent()); true } catch (e: Exception) { fileCallback = null; false }
            }
        }
        handleIntent(intent) ?: web.loadUrl(home)
    }

    // Pull-to-refresh only on the landing page. Studio (forms, uploads, crop), scanner and player must never be reloaded by a swipe.
    private fun pullAllowed(url: String): Boolean { val path = Uri.parse(url).path ?: "/"; return path == "/" || path == "" || path.endsWith("/index.html") }
    private fun handleIntent(i: Intent?): Unit? { val d = i?.data ?: return null; web.loadUrl(d.toString()); return Unit }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
}
