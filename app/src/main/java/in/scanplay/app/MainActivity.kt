package `in`.scanplay.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
        swipe.setColorSchemeColors(0xFF7C3AED.toInt(), 0xFFFF4D6D.toInt())
        swipe.setOnRefreshListener { web.reload() }

        web.settings.apply {
            javaScriptEnabled = true; domStorageEnabled = true; databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false          // AR player autoplays video
            allowFileAccess = true; loadWithOverviewMode = true; useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = userAgentString + " ScanPlayApp/1.0"
        }
        WebView.setWebContentsDebuggingEnabled(false)

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                return if (u.host == "scanplay.in" || u.host?.endsWith(".scanplay.in") == true) false
                else { startActivity(Intent(Intent.ACTION_VIEW, u)); true }   // WhatsApp, Razorpay UPI apps, Drive etc.
            }
            override fun onPageFinished(view: WebView, url: String) { swipe.isRefreshing = false; swipe.isEnabled = !url.contains("view.html") }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {   // camera for AR
                if (request.resources.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) request.grant(request.resources)
                    else { pendingPermission = request; cameraPermission.launch(Manifest.permission.CAMERA) }
                } else request.deny()
            }
            override fun onShowFileChooser(w: WebView, cb: ValueCallback<Array<Uri>>, p: FileChooserParams): Boolean {   // photo/video uploads
                fileCallback?.onReceiveValue(null); fileCallback = cb
                return try { filePicker.launch(p.createIntent()); true } catch (e: Exception) { fileCallback = null; false }
            }
        }
        handleIntent(intent) ?: web.loadUrl(home)
    }

    private fun handleIntent(i: Intent?): Unit? { val d = i?.data ?: return null; web.loadUrl(d.toString()); return Unit }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() else super.onBackPressed() }
    override fun onPause() { super.onPause(); web.onPause() }
    override fun onResume() { super.onResume(); web.onResume() }
}
