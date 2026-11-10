package com.d0ll1ii.dollxrstore.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.DisplayMetrics
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.d0ll1ii.dollxrstore.auth.GoogleAuthManager
import com.d0ll1ii.dollxrstore.R
import com.d0ll1ii.dollxrstore.github.GitHubGamesClient
import com.d0ll1ii.dollxrstore.github.GitHubTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Locale

class DollXRBridge(
    private val activity: ComponentActivity,
    private val webView: WebView,
    private val launchPicker: () -> Unit
) {
    private val prefs = activity.getSharedPreferences("dollxr_store", Context.MODE_PRIVATE)
    private val githubTokens = GitHubTokenStore(activity)
    private val githubGames = GitHubGamesClient(
        activity,
        activity.getString(R.string.github_app_client_id),
        activity.getString(R.string.github_repo_owner),
        activity.getString(R.string.github_repo_name),
        githubTokens
    )
    private var pendingPick: Pair<String, Int>? = null

    // Synchronous JS Methods

    @JavascriptInterface
    fun getBridgeVersion(): String = appVersion()

    @JavascriptInterface
    fun getDeviceInfo(): String {
        val dm: DisplayMetrics = activity.resources.displayMetrics
        val vr = activity.packageManager.run {
            hasSystemFeature(PackageManager.FEATURE_VR_MODE_HIGH_PERFORMANCE) ||
                hasSystemFeature("android.hardware.vr.headtracking") ||
                hasSystemFeature(PackageManager.FEATURE_VR_MODE)
        }
        return JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("appVersion", appVersion())
            put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "—")
            put("vrReady", if (vr) "yes" else "no")
            put("locale", Locale.getDefault().toLanguageTag())
            put("screen", "${dm.widthPixels}×${dm.heightPixels}")
        }.toString()
    }

    @JavascriptInterface
    fun openExternal(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val uri = Uri.parse(url)
        if (uri.scheme != "http" && uri.scheme != "https") return false
        return try {
            activity.runOnUiThread {
                try {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (_: Exception) {}
            }
            true
        } catch (_: Exception) { false }
    }

    @JavascriptInterface
    fun saveSettings(key: String, json: String?) {
        prefs.edit().apply {
            if (json.isNullOrEmpty()) remove(key) else putString(key, json)
        }.apply()
    }

    @JavascriptInterface
    fun loadSettings(key: String): String = prefs.getString(key, "") ?: ""

    // Asynchronous JS Methods

    @JavascriptInterface
    fun syncProfile(id: String, json: String?) {
        resolve(id, "{}")
    }

    @JavascriptInterface
    fun launchVR(id: String, json: String?) {
        activity.runOnUiThread {
            try {
                resolve(id, JSONObject().put("active", true).toString())
            } catch (e: Exception) {
                reject(id, "VR_FAILED", e.message)
            }
        }
    }

    @JavascriptInterface
    fun pickImage(id: String, json: String?) {
        val max = try { JSONObject(json ?: "{}").optInt("maxSize", 2048) } catch (_: Exception) { 2048 }
        pendingPick = id to max
        activity.runOnUiThread { launchPicker() }
    }

    fun onImagePicked(uri: Uri?) {
        val (id, max) = pendingPick ?: return
        pendingPick = null
        if (uri == null) { reject(id, "CANCELLED", "cancelled"); return }
        activity.lifecycleScope.launch {
            try {
                val dataUrl = withContext(Dispatchers.IO) { decodeToDataUrl(uri, max) }
                resolve(id, JSONObject().put("dataUrl", dataUrl).toString())
            } catch (e: Exception) {
                reject(id, "PICK_FAILED", e.message)
            }
        }
    }

    @JavascriptInterface
    fun googleSignIn(id: String, json: String?) {
        activity.lifecycleScope.launch {
            val result = GoogleAuthManager.signInWithGoogle(activity)
            result.onSuccess { profile ->
                resolve(id, JSONObject().apply {
                    put("id", profile.uid)
                    put("displayName", profile.displayName)
                    put("email", profile.email)
                    put("photoUrl", profile.photoUrl)
                    put("idToken", profile.uid)
                }.toString())
            }.onFailure { error ->
                reject(id, "G_ERR", error.message ?: "SignIn Cancelled or Failed")
            }
        }
    }

    @JavascriptInterface
    fun githubStatus(id: String, json: String?) {
        activity.lifecycleScope.launch {
            try {
                val status = withContext(Dispatchers.IO) {
                    JSONObject()
                        .put("configured", githubGames.isConfigured())
                        .put("authorized", githubGames.hasSavedToken())
                }
                resolve(id, status.toString())
            } catch (e: Exception) { reject(id, "GITHUB_STATUS_FAILED", e.message) }
        }
    }

    @JavascriptInterface
    fun githubBeginAuth(id: String, json: String?) {
        activity.lifecycleScope.launch {
            try {
                val challenge = withContext(Dispatchers.IO) { githubGames.beginDeviceFlow() }
                resolve(id, challenge.toString())
            } catch (e: Exception) { reject(id, "GITHUB_AUTH_FAILED", e.message) }
        }
    }

    @JavascriptInterface
    fun githubPollAuth(id: String, json: String?) {
        activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { githubGames.pollDeviceFlow() }
                resolve(id, result.toString())
            } catch (e: Exception) { reject(id, "GITHUB_AUTH_FAILED", e.message) }
        }
    }

    @JavascriptInterface
    fun githubLoadGames(id: String, json: String?) {
        activity.lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    JSONObject().put("games", githubGames.getCatalog())
                }
                resolve(id, result.toString())
            } catch (e: Exception) { reject(id, "GITHUB_CATALOG_FAILED", e.message) }
        }
    }

    @JavascriptInterface
    fun githubLoadImage(id: String, json: String?) {
        activity.lifecycleScope.launch {
            try {
                val fileName = JSONObject(json ?: "{}").optString("file")
                val dataUrl = withContext(Dispatchers.IO) { githubGames.getImage(fileName) }
                resolve(id, JSONObject().put("dataUrl", dataUrl).toString())
            } catch (e: Exception) { reject(id, "GITHUB_IMAGE_FAILED", e.message) }
        }
    }

    @JavascriptInterface
    fun githubDownloadGame(id: String, json: String?) {
        activity.lifecycleScope.launch {
            try {
                val fileName = JSONObject(json ?: "{}").optString("file")
                val savedAs = withContext(Dispatchers.IO) { githubGames.downloadGame(fileName) }
                resolve(id, JSONObject().put("fileName", savedAs).toString())
            } catch (e: Exception) { reject(id, "GITHUB_DOWNLOAD_FAILED", e.message) }
        }
    }

    // Utilities

    private fun appVersion(): String = try {
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "1.0"
    } catch (_: Exception) { "1.0" }

    private fun decodeToDataUrl(uri: Uri, maxSize: Int): String {
        val resolver = activity.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / sample > maxSize * 2 || bounds.outHeight / sample > maxSize * 2) sample *= 2
        var bmp = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: throw IllegalStateException("decode failed")
        val longest = maxOf(bmp.width, bmp.height)
        if (longest > maxSize) {
            val k = maxSize.toFloat() / longest
            bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
        }
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return "data:image/jpeg;base64," + Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private fun resolve(id: String, json: String) = callback(id, true, json)
    private fun reject(id: String, code: String, message: String?) =
        callback(id, false, JSONObject().put("code", code).put("message", message ?: code).toString())

    private fun callback(id: String, ok: Boolean, json: String) {
        val js = "if(window.DollXRBridge){window.DollXRBridge.onResult(${JSONObject.quote(id)},$ok,${JSONObject.quote(json)})}"
        webView.post { webView.evaluateJavascript(js, null) }
    }
}
