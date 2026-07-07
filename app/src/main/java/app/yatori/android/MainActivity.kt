package app.yatori.android

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.webkit.WebViewAssetLoader
import mobilecore.Mobilecore
import java.io.File

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private val examCodeHandler = Handler(Looper.getMainLooper())
    private var examCodeDialog: AlertDialog? = null
    private var activeExamCodeRequestId: String? = null
    private val snoozedExamCodeRequests = mutableMapOf<String, Long>()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermission()

        webView = WebView(this)
        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()
        WebView.setWebContentsDebuggingEnabled(true)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                return request?.url?.let { assetLoader.shouldInterceptRequest(it) }
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "WebView load error: ${error?.errorCode} ${error?.description} url=${request.url}")
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                Log.d(
                    TAG,
                    "Web console: ${consoleMessage?.message()} (${consoleMessage?.sourceId()}:${consoleMessage?.lineNumber()})",
                )
                return true
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?,
            ): Boolean {
                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = filePathCallback
                return try {
                    val intent = fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                    startActivityForResult(intent, REQUEST_FILE_CHOOSER)
                    true
                } catch (_: Throwable) {
                    this@MainActivity.filePathCallback = null
                    false
                }
            }
        }
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = true
        webView.settings.allowContentAccess = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            webView.settings.forceDark = WebSettings.FORCE_DARK_OFF
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            webView.settings.isAlgorithmicDarkeningAllowed = false
        }
        webView.addJavascriptInterface(YatoriBridge(this), "YatoriAndroidBridge")
        setContentView(webView)
        webView.loadUrl(APP_URL)
        showBlankPageHintIfNeeded()
        initEngineAsync()
    }

    private fun showBlankPageHintIfNeeded() {
        Handler(Looper.getMainLooper()).postDelayed({
            webView.evaluateJavascript(
                "(function(){return !!document.querySelector('[data-yatori-mounted=\"true\"]') || document.body.innerText.trim().length > 0})()",
            ) { raw ->
                if (raw != "true") {
                    Log.e(TAG, "Blank WebView detected after startup")
                    webView.loadDataWithBaseURL(
                        APP_URL,
                        """
                        <!doctype html>
                        <html lang="zh-CN">
                        <meta charset="utf-8">
                        <meta name="viewport" content="width=device-width,initial-scale=1">
                        <body style="margin:0;font-family:sans-serif;background:#f6f8fb;color:#172033">
                          <div style="padding:24px">
                            <h2>Yatori 页面加载失败</h2>
                            <p>本机 WebView 没有成功加载前端资源。</p>
                            <p>请用 adb 抓取日志：</p>
                            <pre style="white-space:pre-wrap;background:#fff;border:1px solid #d8dee8;padding:12px;border-radius:8px">adb logcat -d -s YatoriAndroid AndroidRuntime chromium</pre>
                          </div>
                        </body>
                        </html>
                        """.trimIndent(),
                        "text/html",
                        "UTF-8",
                        null,
                    )
                }
            }
        }, 5000)
    }

    private fun initEngineAsync() {
        Thread {
            try {
                val dataDir = File(filesDir, "yatori").absolutePath
                Log.i(TAG, "Initializing engine at $dataDir")
                val result = Mobilecore.init(dataDir)
                val ok = org.json.JSONObject(result).optBoolean("ok")
                if (!ok) {
                    throw IllegalStateException(org.json.JSONObject(result).optString("error", "init failed"))
                }
                validateBundledCoreMetadata()
                EngineRegistry.initialized = true
                Log.i(TAG, "Mobile core initialized")
                runOnUiThread {
                    webView.evaluateJavascript("window.dispatchEvent(new Event('yatori-engine-ready'))", null)
                    startExamCodePolling()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Engine init failed", t)
                val message = org.json.JSONObject.quote(t.message ?: t.javaClass.simpleName)
                runOnUiThread {
                    Toast.makeText(this, "Yatori 初始化失败：${t.message ?: t.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    webView.evaluateJavascript("window.dispatchEvent(new CustomEvent('yatori-engine-error', { detail: $message }))", null)
                }
            }
        }.start()
    }

    private fun startExamCodePolling() {
        examCodeHandler.removeCallbacksAndMessages(null)
        examCodeHandler.postDelayed(object : Runnable {
            override fun run() {
                pollExamCodeRequests()
                examCodeHandler.postDelayed(this, 2000)
            }
        }, 1000)
    }

    private fun pollExamCodeRequests() {
        if (!EngineRegistry.initialized || isFinishing || isDestroyed) return
        Thread {
            try {
                val result = Mobilecore.listXXTExamCodeRequestsJSON()
                val obj = org.json.JSONObject(result)
                if (!obj.optBoolean("ok")) {
                    return@Thread
                }
                val arr = obj.optJSONArray("data") ?: org.json.JSONArray()
                val now = System.currentTimeMillis()
                var target: org.json.JSONObject? = null
                var index = 0
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id")
                    if (item.optString("status") == "pending" && (snoozedExamCodeRequests[id] ?: 0L) <= now) {
                        target = item
                        index = i + 1
                        break
                    }
                }
                val pending = target ?: return@Thread
                Log.i(TAG, "exam code pending found id=${pending.optString("id")} total=${arr.length()}")
                runOnUiThread {
                    showExamCodeDialog(pending, index, arr.length())
                }
            } catch (t: Throwable) {
                Log.w(TAG, "poll exam code failed", t)
            }
        }.start()
    }

    private fun showExamCodeDialog(req: org.json.JSONObject, index: Int, total: Int) {
        val id = req.optString("id")
        if (id.isBlank() || activeExamCodeRequestId == id || examCodeDialog?.isShowing == true) {
            return
        }
        activeExamCodeRequestId = id
        val account = req.optString("account", req.optString("uid", ""))
        val examName = req.optString("examName", "")
        val taskRefId = req.optString("taskRefId", "")

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(16))
            background = roundedBg(Color.WHITE, dp(18), 0)
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = FrameLayout(this).apply {
            background = roundedBg(Color.rgb(232, 240, 255), dp(14), 0)
            addView(ImageView(this@MainActivity).apply {
                setImageResource(resources.getIdentifier("ic_exam_key", "drawable", packageName))
                alpha = 0.95f
            }, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        }
        header.addView(icon, LinearLayout.LayoutParams(dp(44), dp(44)))
        val titleBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        titleBox.addView(TextView(this).apply {
            text = "需要输入考试码"
            textSize = 19f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(23, 32, 51))
        })
        titleBox.addView(TextView(this).apply {
            text = if (total > 1) "待处理 $index/$total · 仅暂停当前考试" else "仅暂停当前考试，其他任务继续"
            textSize = 12.5f
            setTextColor(Color.rgb(95, 107, 124))
            setPadding(0, dp(3), 0, 0)
        })
        header.addView(titleBox, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(header)

        card.addView(TextView(this).apply {
            text = "检测到「$examName」需要考试码，请输入后继续执行。"
            textSize = 14f
            setTextColor(Color.rgb(71, 84, 103))
            setPadding(0, dp(14), 0, dp(10))
        })

        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = roundedBg(Color.rgb(246, 248, 251), dp(12), Color.rgb(224, 230, 240))
        }
        info.addView(metaLine("账号", account))
        info.addView(metaLine("考试", examName))
        if (taskRefId.isNotBlank()) info.addView(metaLine("任务", taskRefId))
        card.addView(info, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = dp(14)
        })

        val input = EditText(this).apply {
            hint = "例如：t8918554"
            setSingleLine(true)
            textSize = 16f
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.rgb(23, 32, 51))
            setHintTextColor(Color.rgb(152, 162, 179))
            setPadding(dp(14), 0, dp(14), 0)
            background = roundedBg(Color.rgb(250, 252, 255), dp(12), Color.rgb(37, 99, 235))
        }
        card.addView(input, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val cancelBtn = dialogButton("取消考试", Color.rgb(220, 38, 38), Color.TRANSPARENT, false)
        val laterBtn = dialogButton("稍后处理", Color.rgb(71, 84, 103), Color.rgb(241, 245, 249), false)
        val submitBtn = dialogButton("提交并继续", Color.WHITE, Color.rgb(37, 99, 235), true)
        actions.addView(cancelBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        actions.addView(laterBtn, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        actions.addView(submitBtn, LinearLayout.LayoutParams(0, dp(44), 1.25f))
        card.addView(actions)

        examCodeDialog = AlertDialog.Builder(this)
            .setView(card)
            .create()
        laterBtn.setOnClickListener {
            examCodeDialog?.let { dialog ->
                snoozedExamCodeRequests[id] = System.currentTimeMillis() + 15000
                activeExamCodeRequestId = null
                dialog.dismiss()
            }
        }
        cancelBtn.setOnClickListener {
            examCodeDialog?.let { dialog ->
                answerOrCancelExamCode(id, null)
                activeExamCodeRequestId = null
                dialog.dismiss()
            }
        }
        submitBtn.setOnClickListener {
            examCodeDialog?.let { dialog ->
                val code = input.text?.toString()?.trim().orEmpty()
                if (code.isBlank()) {
                    input.requestFocus()
                    Toast.makeText(this, "请输入考试码", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                answerOrCancelExamCode(id, code)
                activeExamCodeRequestId = null
                dialog.dismiss()
            }
        }
        examCodeDialog?.setOnShowListener {
            examCodeDialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            examCodeDialog?.window?.setDimAmount(0.46f)
            input.requestFocus()
        }
        examCodeDialog?.setOnDismissListener {
            if (activeExamCodeRequestId == id) {
                activeExamCodeRequestId = null
            }
        }
        examCodeDialog?.show()
    }

    private fun metaLine(label: String, value: String): TextView {
        return TextView(this).apply {
            text = "$label：$value"
            textSize = 13.5f
            setTextColor(Color.rgb(52, 64, 84))
            setPadding(0, dp(2), 0, dp(2))
        }
    }

    private fun dialogButton(textValue: String, textColor: Int, bgColor: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = textValue
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
            typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            background = roundedBg(bgColor, dp(12), if (bgColor == Color.TRANSPARENT) Color.rgb(226, 232, 240) else 0)
        }
    }

    private fun roundedBg(color: Int, radius: Int, strokeColor: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius.toFloat()
            setColor(color)
            if (strokeColor != 0) {
                setStroke(dp(1), strokeColor)
            }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun answerOrCancelExamCode(id: String, code: String?) {
        Thread {
            try {
                val result = if (code == null) {
                    Mobilecore.cancelXXTExamCodeRequest(id)
                } else {
                    Mobilecore.answerXXTExamCodeRequest(id, code)
                }
                val obj = org.json.JSONObject(result)
                val ok = obj.optBoolean("ok")
                val msg = if (ok) "考试码已提交" else obj.optString("error", "考试码处理失败")
                runOnUiThread {
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "answer exam code failed", t)
                runOnUiThread {
                    Toast.makeText(this, "考试码处理失败：${t.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun validateBundledCoreMetadata() {
        val schemaText = assets.open("core/api-schema.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val versionText = assets.open("core/yatori-core-version.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        val schema = org.json.JSONObject(schemaText)
        val version = org.json.JSONObject(versionText)
        val schemaVersion = schema.optInt("schemaVersion", -1)
        if (schemaVersion != SUPPORTED_API_SCHEMA_VERSION) {
            throw IllegalStateException("mobilecore schemaVersion=$schemaVersion, app supports $SUPPORTED_API_SCHEMA_VERSION")
        }
        Log.i(
            TAG,
            "Bundled core version=${version.optString("desktopCoreVersion")} commit=${version.optString("coreCommit")} schema=$schemaVersion aar=${version.optString("aarFile")}",
        )
    }

    @Deprecated("Deprecated by Android framework, used for WebView file chooser compatibility.")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_FILE_CHOOSER) {
            val result = WebChromeClient.FileChooserParams.parseResult(resultCode, data)
            filePathCallback?.onReceiveValue(result)
            filePathCallback = null
        } else if (requestCode == REQUEST_ACCOUNT_DB_IMPORT) {
            val uri = data?.data
            if (resultCode == RESULT_OK && uri != null) {
                importAccountDatabase(uri)
            }
        }
    }

    fun openAccountDatabasePicker(): String {
        runOnUiThread {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "application/x-sqlite3", "*/*"))
            }
            startActivityForResult(intent, REQUEST_ACCOUNT_DB_IMPORT)
        }
        return """{"ok":true,"data":"已打开账号数据库选择器","error":"","code":""}"""
    }

    private fun importAccountDatabase(uri: Uri) {
        Thread {
            val result = try {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    ?: return@Thread dispatchAccountImportResult("""{"ok":false,"data":null,"error":"无法读取账号数据库","code":"IMPORT_READ_FAILED"}""")
                val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Mobilecore.importAccountsDBBase64(encoded)
            } catch (t: Throwable) {
                """{"ok":false,"data":null,"error":${org.json.JSONObject.quote(t.message ?: t.javaClass.simpleName)},"code":"ANDROID_IMPORT_FAILED"}"""
            }
            dispatchAccountImportResult(result)
        }.start()
    }

    fun openUrl(url: String): String {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return """{"ok":false,"error":"仅允许 http:// 或 https:// 链接"}"""
        }
        runOnUiThread {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        return """{"ok":true}"""
    }

    fun getSystemTheme(): String {
        val mode = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return if (mode == Configuration.UI_MODE_NIGHT_YES) "dark" else "light"
    }

    fun refreshForegroundService() {
        val intent = Intent(this, YatoriForegroundService::class.java).setAction(YatoriForegroundService.ACTION_REFRESH)
        runOnUiThread {
            ContextCompat.startForegroundService(this, intent)
        }
    }

    fun copyText(label: String, text: String): String {
        runOnUiThread {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
            Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
        }
        return """{"ok":true,"data":${org.json.JSONObject.quote(text)}}"""
    }

    fun shareTextFile(fileName: String, content: String): String {
        val file = File(cacheDir, fileName)
        file.writeText(content)
        return shareFile(file, "text/yaml", "导出配置")
    }

    fun shareBase64File(fileName: String, encoded: String, mimeType: String): String {
        val file = File(cacheDir, fileName)
        file.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
        return shareFile(file, mimeType, "导出账号数据")
    }

    private fun shareFile(file: File, mimeType: String, title: String): String {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runOnUiThread {
            startActivity(Intent.createChooser(intent, title))
        }
        return """{"ok":true}"""
    }

    fun resolveBridgeCallback(callbackId: String, result: String) {
        val script = "window.__yatoriBridgeResolve && window.__yatoriBridgeResolve(${org.json.JSONObject.quote(callbackId)}, ${org.json.JSONObject.quote(result)})"
        runOnUiThread {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun dispatchAccountImportResult(result: String) {
        runOnUiThread {
            val ok = try { org.json.JSONObject(result).optBoolean("ok") } catch (_: Throwable) { false }
            val message = try {
                val obj = org.json.JSONObject(result)
                if (ok) {
                    val data = obj.optJSONObject("data")
                    if (data != null) {
                        "账号导入完成：新增 ${data.optInt("imported")}，更新 ${data.optInt("updated")}，跳过 ${data.optInt("skipped")}"
                    } else {
                        obj.optString("data", "账号导入完成")
                    }
                } else {
                    obj.optString("error", "账号导入失败")
                }
            } catch (_: Throwable) {
                "账号导入失败"
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            val script = "window.dispatchEvent(new CustomEvent('yatori-account-imported', { detail: ${org.json.JSONObject.quote(result)} }))"
            webView.evaluateJavascript(script, null)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    companion object {
        private const val TAG = "YatoriAndroid"
        private const val APP_URL = "https://appassets.androidplatform.net/assets/index.html"
        private const val REQUEST_FILE_CHOOSER = 2001
        private const val REQUEST_ACCOUNT_DB_IMPORT = 2002
        private const val SUPPORTED_API_SCHEMA_VERSION = 1
    }
}
