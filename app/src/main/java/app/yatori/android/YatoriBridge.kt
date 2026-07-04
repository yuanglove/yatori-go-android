package app.yatori.android

import android.webkit.JavascriptInterface
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class YatoriBridge(private val activity: MainActivity) {
    @JavascriptInterface
    fun getSystemTheme(): String = activity.getSystemTheme()

    @JavascriptInterface
    fun call(method: String, payload: String): String {
        return try {
            val args = JSONArray(payload)
            val engine = EngineRegistry.engine ?: return error("Engine 未初始化")
            when (method) {
                "GetConfig" -> engine.getConfig()
                "SaveConfig" -> engine.saveConfig(args.getJSONObject(0).toString())
                "GetDataDir" -> ok(activity.filesDir.resolve("yatori").absolutePath)
                "OpenDataDir" -> activity.copyText("Yatori 数据目录", activity.filesDir.resolve("yatori").absolutePath)
                "ImportConfig" -> error("Android 请使用系统文件选择器导入配置")
                "ImportConfigText" -> engine.importConfigFromText(args.getString(0))
                "ImportAccountsDBBase64" -> engine.importAccountsDBBase64(args.getString(0))
                "ImportAccountData" -> activity.openAccountDatabasePicker()
                "ExportConfig" -> {
                    val saved = JSONObject(engine.saveConfig(args.getJSONObject(0).toString()))
                    if (!saved.optBoolean("ok")) saved.toString() else {
                        val exported = JSONObject(engine.exportConfigText())
                        if (!exported.optBoolean("ok")) exported.toString()
                        else activity.shareTextFile("yatori-config.yaml", exported.optString("data"))
                    }
                }
                "ExportAccountData" -> {
                    val exported = JSONObject(engine.exportAccountsDBBase64())
                    if (!exported.optBoolean("ok")) exported.toString()
                    else activity.shareBase64File("yatori.db", exported.optString("data"), "application/octet-stream")
                }
                "ListAccounts" -> engine.listAccounts()
                "AddAccount" -> engine.addAccount(args.getJSONObject(0).toString())
                "UpdateAccount" -> engine.updateAccount(args.getJSONObject(0).toString())
                "DeleteAccount" -> engine.deleteAccount(args.getString(0))
                "StartTask" -> {
                    val result = engine.startTask(args.getString(0))
                    activity.refreshForegroundService()
                    result
                }
                "StopTask" -> {
                    val result = engine.stopTask(args.getString(0))
                    activity.refreshForegroundService()
                    result
                }
                "GetTaskStatuses" -> engine.getTaskStatuses()
                "GetDashboard" -> engine.getDashboard()
                "TailLogFile" -> engine.tailLogFile(args.optLong(0, 200))
                "GetRecentLogs" -> engine.getRecentLogs(args.optLong(0, 200))
                "GetPlatformSupport" -> engine.getPlatformSupport()
                "TestAIConfig" -> engine.testAIConfig()
                "TestQuestionBankConfig" -> engine.testQuestionBankConfig()
                "CheckForUpdates" -> checkForUpdates(args.optString(0))
                "OpenURL" -> activity.openUrl(args.getString(0))
                "StartICVECookieCapture" -> error("Android 当前支持手动填写 ICVE Cookie")
                "ReadICVECookie" -> error("Android 当前支持手动填写 ICVE Cookie")
                "GetCourses" -> engine.getCourses(args.getString(0))
                else -> error("未知方法: $method")
            }
        } catch (t: Throwable) {
            error(t.message ?: t.javaClass.simpleName)
        }
    }

    @JavascriptInterface
    fun callAsync(method: String, payload: String, callbackId: String) {
        Thread {
            val result = call(method, payload)
            activity.resolveBridgeCallback(callbackId, result)
        }.start()
    }

    private fun ok(data: Any): String {
        val obj = JSONObject()
        obj.put("ok", true)
        obj.put("data", data)
        return obj.toString()
    }

    private fun error(message: String): String {
        val obj = JSONObject()
        obj.put("ok", false)
        obj.put("error", message)
        return obj.toString()
    }

    private fun checkForUpdates(currentVersion: String): String {
        val current = normalizeVersion(currentVersion)
        val releaseUrl = "https://api.github.com/repos/yuanglove/yatori-go-android/releases/latest"
        val releasesPage = "https://github.com/yuanglove/yatori-go-android/releases"
        return try {
            val connection = (URL(releaseUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12000
                readTimeout = 12000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "yatori-go-android")
            }
            val status = connection.responseCode
            if (status == 404) {
                return ok(JSONObject(mapOf(
                    "hasUpdate" to false,
                    "latestVersion" to current,
                    "currentVersion" to current,
                    "url" to releasesPage,
                )))
            }
            if (status !in 200..299) {
                return error("GitHub 更新检查失败: HTTP $status")
            }
            val body = connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val release = JSONObject(body)
            val latest = normalizeVersion(release.optString("tag_name", current))
            val url = release.optString("html_url", releasesPage).ifBlank { releasesPage }
            ok(JSONObject(mapOf(
                "hasUpdate" to (compareVersions(latest, current) > 0),
                "latestVersion" to latest,
                "currentVersion" to current,
                "url" to url,
            )))
        } catch (t: Throwable) {
            error(t.message ?: t.javaClass.simpleName)
        }
    }

    private fun normalizeVersion(value: String): String =
        value.trim().removePrefix("v").ifBlank { "0.0.0" }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val b = right.split(".", "-", "_").map { it.toIntOrNull() ?: 0 }
        val max = maxOf(a.size, b.size)
        for (i in 0 until max) {
            val av = a.getOrElse(i) { 0 }
            val bv = b.getOrElse(i) { 0 }
            if (av != bv) return av.compareTo(bv)
        }
        return 0
    }
}
