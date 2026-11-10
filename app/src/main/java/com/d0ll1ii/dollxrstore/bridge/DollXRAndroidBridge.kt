package com.d0ll1ii.dollxrstore.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.ComponentActivity
import com.d0ll1ii.dollxrstore.auth.GoogleAuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class DollXRAndroidBridge(
    private val activity: ComponentActivity,
    private val webView: WebView
) {
    private val prefs = activity.getSharedPreferences("dollxr_html_prefs", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(Dispatchers.Main)

    @JavascriptInterface
    fun getBridgeVersion(): String {
        return "1.0"
    }

    @JavascriptInterface
    fun getDeviceInfo(): String {
        val json = JSONObject().apply {
            put("manufacturer", Build.MANUFACTURER)
            put("model", Build.MODEL)
            put("androidVersion", Build.VERSION.RELEASE)
            put("sdk", Build.VERSION.SDK_INT)
            put("appVersion", "1.0.0")
            put("abi", Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
            put("vrReady", true)
            put("locale", activity.resources.configuration.locales[0].toLanguageTag())
        }
        return json.toString()
    }

    @JavascriptInterface
    fun googleSignIn(id: String, payloadJson: String) {
        scope.launch {
            val result = GoogleAuthManager.signInWithGoogle(activity)
            result.onSuccess { profile ->
                val resJson = JSONObject().apply {
                    put("name", profile.displayName)
                    put("email", profile.email)
                    put("avatar", profile.photoUrl)
                    put("idToken", profile.uid)
                    put("id", profile.uid)
                }
                sendResult(id, true, resJson.toString())
            }.onFailure { error ->
                val errJson = JSONObject().apply {
                    put("code", "G_ERR")
                    put("message", error.message ?: "SignIn Cancelled or Failed")
                }
                sendResult(id, false, errJson.toString())
            }
        }
    }

    @JavascriptInterface
    fun saveSettings(key: String, json: String) {
        prefs.edit().putString(key, json).apply()
    }

    @JavascriptInterface
    fun loadSettings(key: String): String {
        return prefs.getString(key, "") ?: ""
    }

    @JavascriptInterface
    fun openExternal(url: String): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            activity.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    @JavascriptInterface
    fun launchVR(id: String, payloadJson: String) {
        scope.launch {
            val resJson = JSONObject().apply { put("active", true) }
            sendResult(id, true, resJson.toString())
        }
    }

    @JavascriptInterface
    fun syncProfile(id: String, payloadJson: String) {
        scope.launch {
            sendResult(id, true, JSONObject().apply { put("synced", true) }.toString())
        }
    }

    private fun sendResult(id: String, ok: Boolean, jsonResult: String) {
        val escapedJson = jsonResult.replace("'", "\\'")
        val jsCall = "if (window.DollXRBridge) { window.DollXRBridge.onResult('$id', $ok, '$escapedJson'); }"
        webView.post {
            webView.evaluateJavascript(jsCall, null)
        }
    }
}
