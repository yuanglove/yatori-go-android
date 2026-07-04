package app.yatori.android

import android.webkit.JavascriptInterface
import mobilecore.Mobilecore
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
            if (!EngineRegistry.initialized && method !in noCoreMethods) {
                return error("CORE_NOT_INITIALIZED", "Go Mobile Core 未初始化")
            }
            when (method) {
                "GetConfig" -> core(Mobilecore.getConfigJSON())
                "SaveConfig" -> core(Mobilecore.saveConfigJSON(args.getJSONObject(0).toString()))
                "GetDataDir" -> ok(activity.filesDir.resolve("yatori").absolutePath)
                "OpenDataDir" -> activity.copyText("Yatori 数据目录", activity.filesDir.resolve("yatori").absolutePath)
                "ImportConfig" -> error("UNSUPPORTED", "Android 请使用系统文件选择器导入配置")
                "ImportConfigText" -> core(Mobilecore.importConfigText(args.getString(0)))
                "ImportAccountsDBBase64" -> core(Mobilecore.importAccountsDBBase64(args.getString(0)))
                "ImportAccountData" -> activity.openAccountDatabasePicker()
                "ExportConfig" -> exportConfig(args)
                "ExportAccountData" -> exportAccounts()
                "ListAccounts" -> core(Mobilecore.listAccountsJSON())
                "AddAccount" -> core(Mobilecore.addAccountJSON(args.getJSONObject(0).toString()))
                "UpdateAccount" -> core(Mobilecore.updateAccountJSON(args.getJSONObject(0).toString()))
                "DeleteAccount" -> core(Mobilecore.deleteAccount(args.getString(0)))
                "StartTask" -> {
                    val result = core(Mobilecore.startTask(args.getString(0)))
                    activity.refreshForegroundService()
                    result
                }
                "StopTask" -> {
                    val result = core(Mobilecore.stopTask(args.getString(0)))
                    activity.refreshForegroundService()
                    result
                }
                "GetTaskStatuses" -> core(Mobilecore.getTaskStatusesJSON())
                "GetDashboard" -> buildDashboard()
                "TailLogFile" -> core(Mobilecore.getRecentLogsJSON(args.optLong(0, 200)))
                "GetRecentLogs" -> core(Mobilecore.getRecentLogsJSON(args.optLong(0, 200)))
                "GetPlatformSupport" -> core(Mobilecore.getCapabilities())
                "GetCoreVersion" -> core(Mobilecore.getCoreVersionJSON())
                "TestAIConfig" -> core(Mobilecore.testAIJSON())
                "TestQuestionBankConfig" -> core(Mobilecore.testQuestionBankJSON())
                "CheckForUpdates" -> checkForUpdates(args.optString(0))
                "OpenURL" -> activity.openUrl(args.getString(0))
                "StartICVECookieCapture" -> error("UNSUPPORTED", "Android 当前请手动填写 ICVE Cookie")
                "ReadICVECookie" -> error("UNSUPPORTED", "Android 当前请手动填写 ICVE Cookie")
                "GetCourses" -> core(Mobilecore.getCoursesJSON(args.getString(0)))
                else -> error("UNKNOWN_METHOD", "未知方法: $method")
            }
        } catch (t: Throwable) {
            error("ANDROID_BRIDGE_ERROR", t.message ?: t.javaClass.simpleName)
        }
    }

    @JavascriptInterface
    fun callAsync(method: String, payload: String, callbackId: String) {
        Thread {
            val result = call(method, payload)
            activity.resolveBridgeCallback(callbackId, result)
        }.start()
    }

    private fun exportConfig(args: JSONArray): String {
        val config = args.getJSONObject(0)
        val saved = JSONObject(core(Mobilecore.saveConfigJSON(config.toString())))
        if (!saved.optBoolean("ok")) return saved.toString()
        return activity.shareTextFile("yatori-config.json", config.toString(2))
    }

    private fun exportAccounts(): String {
        val exported = JSONObject(core(Mobilecore.exportAccountsDBBase64()))
        if (!exported.optBoolean("ok")) return exported.toString()
        val data = exported.optJSONObject("data") ?: return error("EXPORT_BAD_RESPONSE", "账号导出响应缺少 data")
        return activity.shareBase64File(
            data.optString("fileName", "yatori-accounts.db"),
            data.optString("base64"),
            data.optString("mimeType", "application/octet-stream"),
        )
    }

    private fun buildDashboard(): String {
        val accounts = JSONObject(core(Mobilecore.listAccountsJSON())).optJSONArray("data") ?: JSONArray()
        val tasks = JSONObject(core(Mobilecore.getTaskStatusesJSON())).optJSONArray("data") ?: JSONArray()
        var running = 0
        for (i in 0 until tasks.length()) {
            if (tasks.getJSONObject(i).optString("state") == "running") running++
        }
        val data = JSONObject()
        data.put("totalAccounts", accounts.length())
        data.put("runningTasks", running)
        data.put("configPath", activity.filesDir.resolve("yatori").absolutePath)
        data.put("configOK", true)
        data.put("recentLogs", JSONObject(core(Mobilecore.getRecentLogsJSON(20))).optJSONArray("data") ?: JSONArray())
        return ok(data)
    }

    private fun ok(data: Any): String {
        val obj = JSONObject()
        obj.put("ok", true)
        obj.put("data", data)
        obj.put("error", "")
        obj.put("code", "")
        return obj.toString()
    }

    private fun core(raw: String): String {
        return try {
            normalizeCoreObject(JSONObject(raw), 0)
        } catch (_: Throwable) {
            error("CORE_BAD_RESPONSE", raw)
        }
    }

    private fun normalizeCoreObject(obj: JSONObject, depth: Int): String {
        if (depth > 4) return normalizeEnvelope(obj)
        if (!obj.has("ok")) return ok(obj)
        val nested = obj.opt("data")
        if (nested is JSONObject && nested.has("ok")) {
            return normalizeCoreObject(nested, depth + 1)
        }
        if (nested is String) {
            val text = nested.trim()
            if (text.startsWith("{") && text.endsWith("}")) {
                return try {
                    normalizeCoreObject(JSONObject(text), depth + 1)
                } catch (_: Throwable) {
                    normalizeEnvelope(obj)
                }
            }
        }
        return normalizeEnvelope(obj)
    }

    private fun normalizeEnvelope(obj: JSONObject): String {
        val out = JSONObject()
        out.put("ok", obj.optBoolean("ok", false))
        out.put("data", if (obj.has("data")) obj.opt("data") else JSONObject.NULL)
        out.put("error", obj.optString("error", ""))
        out.put("code", obj.optString("code", ""))
        return out.toString()
    }

    private fun error(code: String, message: String): String {
        val obj = JSONObject()
        obj.put("ok", false)
        obj.put("data", JSONObject.NULL)
        obj.put("error", message)
        obj.put("code", code)
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
                return error("UPDATE_CHECK_FAILED", "GitHub 更新检查失败: HTTP $status")
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
            error("UPDATE_CHECK_FAILED", t.message ?: t.javaClass.simpleName)
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

    companion object {
        private val noCoreMethods = setOf("GetDataDir", "OpenDataDir", "CheckForUpdates", "OpenURL")
    }
}
