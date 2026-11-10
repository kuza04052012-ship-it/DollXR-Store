package com.d0ll1ii.dollxrstore.github

import android.content.ContentValues
import android.content.Context
import android.provider.MediaStore
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

internal data class GitHubDeviceChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresAt: Long,
    var intervalSeconds: Int,
    var lastPollAt: Long = 0L
)

internal class GitHubGamesClient(
    private val context: Context,
    private val clientId: String,
    private val owner: String,
    private val repository: String,
    private val tokens: GitHubTokenStore
) {
    @Volatile private var pendingChallenge: GitHubDeviceChallenge? = null
    private val apiBase get() = "https://api.github.com/repos/$owner/$repository"

    fun isConfigured() = clientId.isNotBlank() &&
        owner.matches(Regex("[A-Za-z0-9_.-]{1,39}")) &&
        repository.matches(Regex("[A-Za-z0-9_.-]{1,100}"))

    fun beginDeviceFlow(): JSONObject {
        check(isConfigured()) { "Configure GitHub App Client ID and repository in local.properties" }
        val response = postForm(
            DEVICE_CODE_URL,
            mapOf("client_id" to clientId, "scope" to "offline_access")
        )
        val challenge = GitHubDeviceChallenge(
            deviceCode = response.getString("device_code"),
            userCode = response.getString("user_code"),
            verificationUri = response.optString("verification_uri", "https://github.com/login/device"),
            expiresAt = System.currentTimeMillis() + response.optLong("expires_in", 900) * 1000L,
            intervalSeconds = response.optInt("interval", 5).coerceAtLeast(5)
        )
        pendingChallenge = challenge
        return JSONObject().apply {
            put("userCode", challenge.userCode)
            put("verificationUri", challenge.verificationUri)
            put("expiresIn", response.optLong("expires_in", 900))
            put("interval", challenge.intervalSeconds)
        }
    }

    @Synchronized
    fun pollDeviceFlow(): JSONObject {
        val challenge = pendingChallenge ?: throw IllegalStateException("Start GitHub sign-in again")
        if (System.currentTimeMillis() >= challenge.expiresAt) {
            pendingChallenge = null
            throw IllegalStateException("GitHub verification code expired. Start sign-in again.")
        }
        val now = System.currentTimeMillis()
        val waitMs = challenge.intervalSeconds * 1000L - (now - challenge.lastPollAt)
        if (waitMs > 0) return JSONObject().put("pending", true).put("waitMs", waitMs)
        challenge.lastPollAt = now
        val result = postForm(
            TOKEN_URL,
            mapOf(
                "client_id" to clientId,
                "device_code" to challenge.deviceCode,
                "grant_type" to DEVICE_GRANT
            )
        )
        when (result.optString("error")) {
            "authorization_pending" -> return JSONObject().put("pending", true).put("waitMs", challenge.intervalSeconds * 1000L)
            "slow_down" -> {
                challenge.intervalSeconds = maxOf(challenge.intervalSeconds + 5, result.optInt("interval", 0))
                return JSONObject().put("pending", true).put("waitMs", challenge.intervalSeconds * 1000L)
            }
            "" -> Unit
            "access_denied" -> { pendingChallenge = null; throw IllegalStateException("GitHub authorization was cancelled") }
            "expired_token", "token_expired" -> { pendingChallenge = null; throw IllegalStateException("GitHub verification code expired. Start sign-in again.") }
            "device_flow_disabled" -> throw IllegalStateException("Enable Device Flow in the GitHub App settings")
            else -> throw IllegalStateException("GitHub authorization failed: ${result.optString("error_description", result.optString("error"))}")
        }
        val access = result.optString("access_token")
        if (access.isBlank()) throw IllegalStateException("GitHub did not return an access token")
        tokens.save(tokensFromResponse(result))
        pendingChallenge = null
        return JSONObject().put("authorized", true)
    }

    fun getCatalog(): JSONArray {
        val token = requireToken()
        val entries = getApiJson("/contents/games", token) as? JSONArray
            ?: throw IOException("GitHub did not return the games folder")
        val files = HashMap<String, JSONObject>()
        val directories = ArrayList<String>()
        for (i in 0 until entries.length()) {
            val entry = entries.optJSONObject(i) ?: continue
            when (entry.optString("type")) {
                "file" -> files[entry.optString("name")] = entry
                "dir" -> directories.add(entry.optString("name"))
            }
        }
        val games = JSONArray()
        for ((filename, _) in files.toSortedMap(String.CASE_INSENSITIVE_ORDER)) {
            if (!filename.endsWith(".dxr", ignoreCase = true)) continue
            val stem = filename.dropLast(4)
            if (stem.isNotBlank()) games.put(makeGame(stem, filename, files.keys, token, stem))
        }
        for (folder in directories.sortedBy { it.lowercase(Locale.ROOT) }) {
            val children = getApiJson("/contents/games/${encodePathSegment(folder)}", token) as? JSONArray ?: continue
            val names = ArrayList<String>()
            for (i in 0 until children.length()) {
                val child = children.optJSONObject(i) ?: continue
                if (child.optString("type") == "file") names.add(child.optString("name"))
            }
            val archives = names.filter { it.endsWith(".dxr", ignoreCase = true) }
            val gameFile = archives.firstOrNull { it.equals("$folder.dxr", ignoreCase = true) }
                ?: archives.singleOrNull()
                ?: continue
            val stem = gameFile.dropLast(4)
            games.put(makeGame(folder, "$folder/$gameFile", names, token, stem, folder))
        }
        return games
    }

    private fun makeGame(
        title: String,
        zipPath: String,
        availableFiles: Collection<String>,
        token: String,
        zipStem: String,
        folder: String = ""
    ): JSONObject {
        fun findFile(vararg candidates: String): String? = candidates.firstNotNullOfOrNull { candidate ->
            availableFiles.firstOrNull { it.equals(candidate, ignoreCase = true) }
        }
        val iconName = if (folder.isBlank()) findFile("$zipStem.png") else findFile("icon.png", "$zipStem.png")
        val descriptionName = if (folder.isBlank()) findFile("$zipStem.txt") else findFile("description.txt", "$zipStem.txt")
        val bannerName = if (folder.isBlank()) findFile("$zipStem-banner.png") else findFile("banner.png", "$zipStem-banner.png")
        fun inFolder(name: String?) = name?.let { if (folder.isBlank()) it else "$folder/$it" }
        return JSONObject().apply {
            put("id", zipPath)
            put("title", title)
            put("gameFile", zipPath)
            put("iconFile", inFolder(iconName) ?: JSONObject.NULL)
            put("description", descriptionName?.let { readText(inFolder(it)!!, token) } ?: "")
            put("bannerFile", inFolder(bannerName) ?: JSONObject.NULL)
        }
    }

    fun getImage(fileName: String): String {
        validateGamePath(fileName)
        require(fileName.endsWith(".png", ignoreCase = true)) { "Invalid image file" }
        val bytes = readSmallFile(fileName, requireToken())
        require(bytes.size <= MAX_IMAGE_BYTES) { "Game image is too large" }
        return "data:image/png;base64," + Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    fun downloadGame(fileName: String): String {
        validateGamePath(fileName)
        require(fileName.endsWith(".dxr", ignoreCase = true)) { "Invalid DXR game file" }
        val token = requireToken()
        val path = "/contents/games/${encodePath(fileName)}"
        val downloadName = fileName.substringAfterLast('/')
        val uri = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, downloadName)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, "Download/DollXRStore")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val destination = resolver.insert(uri, values) ?: throw IOException("Could not create a Downloads file")
        try {
            val connection = openRawApiConnection(path, token)
            try {
                val length = connection.getHeaderFieldLong("Content-Length", -1L)
                if (length > MAX_GAME_FILE_BYTES) throw IOException("DXR is larger than GitHub's supported 100 MB limit")
                resolver.openOutputStream(destination, "w")?.use { output ->
                    connection.inputStream.use { input ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var total = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_GAME_FILE_BYTES) throw IOException("DXR is larger than GitHub's supported 100 MB limit")
                            output.write(buffer, 0, read)
                        }
                    }
                } ?: throw IOException("Could not write the downloaded DXR")
            } finally { connection.disconnect() }
            resolver.update(destination, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
            return downloadName
        } catch (error: Exception) {
            resolver.delete(destination, null, null)
            throw error
        }
    }

    fun hasSavedToken(): Boolean = tokens.read() != null

    private fun readText(fileName: String, token: String): String {
        return try {
            String(readSmallFile(fileName, token), Charsets.UTF_8).trim().take(MAX_DESCRIPTION_CHARS)
        } catch (_: Exception) { "" }
    }

    private fun readSmallFile(fileName: String, token: String): ByteArray {
        validateGamePath(fileName)
        val value = getApiJson("/contents/games/${encodePath(fileName)}", token) as? JSONObject
            ?: throw IOException("GitHub returned invalid file content")
        val content = value.optString("content").replace("\n", "").replace("\r", "")
        if (value.optString("encoding") != "base64" || content.isBlank()) throw IOException("File must be 1 MB or smaller")
        return Base64.decode(content, Base64.DEFAULT)
    }

    @Synchronized
    private fun requireToken(): String {
        val saved = tokens.read() ?: throw IllegalStateException("Connect your GitHub account first")
        if (saved.accessExpiresAt == 0L || saved.accessExpiresAt > System.currentTimeMillis() + TOKEN_REFRESH_MARGIN_MS) {
            return saved.accessToken
        }
        if (saved.refreshToken.isBlank() || saved.refreshExpiresAt <= System.currentTimeMillis()) {
            tokens.clear()
            throw IllegalStateException("GitHub sign-in expired. Connect GitHub again.")
        }
        val refreshed = try {
            postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to clientId,
                    "grant_type" to "refresh_token",
                    "refresh_token" to saved.refreshToken
                )
            )
        } catch (error: Exception) {
            tokens.clear()
            throw IllegalStateException("GitHub sign-in expired. Connect GitHub again.", error)
        }
        if (refreshed.optString("access_token").isBlank()) {
            tokens.clear()
            throw IllegalStateException("GitHub sign-in expired. Connect GitHub again.")
        }
        val updated = tokensFromResponse(refreshed)
        tokens.save(updated)
        return updated.accessToken
    }

    private fun tokensFromResponse(result: JSONObject): GitHubTokens {
        val now = System.currentTimeMillis()
        val accessTtl = result.optLong("expires_in", 0L)
        val refreshTtl = result.optLong("refresh_token_expires_in", 0L)
        return GitHubTokens(
            accessToken = result.getString("access_token"),
            accessExpiresAt = if (accessTtl > 0) now + accessTtl * 1000L else 0L,
            refreshToken = result.optString("refresh_token"),
            refreshExpiresAt = if (refreshTtl > 0) now + refreshTtl * 1000L else 0L
        )
    }

    private fun getApiJson(path: String, token: String): Any {
        val connection = (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = connection.responseCode
            val body = readResponse(connection)
            if (code !in 200..299) {
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) tokens.clear()
                throw apiError(code, body)
            }
            return if (body.trimStart().startsWith("[")) JSONArray(body) else JSONObject(body)
        } finally { connection.disconnect() }
    }

    private fun openRawApiConnection(path: String, token: String): HttpURLConnection {
        var url = URL(apiBase + path)
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = DOWNLOAD_READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/vnd.github.raw+json")
                setRequestProperty("User-Agent", USER_AGENT)
                if (url.host.equals("api.github.com", true) || url.host.equals("raw.githubusercontent.com", true)) {
                    setRequestProperty("Authorization", "Bearer $token")
                    if (url.host.equals("api.github.com", true)) setRequestProperty("X-GitHub-Api-Version", GITHUB_API_VERSION)
                }
            }
            val code = connection.responseCode
            if (code in 200..299) return connection
            val location = connection.getHeaderField("Location")
            if (code in REDIRECT_CODES && !location.isNullOrBlank() && redirectCount < MAX_REDIRECTS) {
                connection.disconnect()
                val next = URL(url, location)
                require(next.protocol == "https" && isAllowedDownloadHost(next.host)) { "GitHub returned an unexpected download URL" }
                url = next
            } else {
                val body = readResponse(connection)
                connection.disconnect()
                if (code == HttpURLConnection.HTTP_UNAUTHORIZED) tokens.clear()
                throw apiError(code, body)
            }
        }
        throw IOException("Too many redirects while downloading the DXR")
    }

    private fun postForm(endpoint: String, values: Map<String, String>): JSONObject {
        val body = values.entries.joinToString("&") { "${formEncode(it.key)}=${formEncode(it.value)}" }
            .toByteArray(Charsets.UTF_8)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            connection.outputStream.use { it.write(body) }
            val code = connection.responseCode
            val response = readResponse(connection)
            val json = try { JSONObject(response) } catch (_: Exception) { JSONObject() }
            if (code !in 200..299) throw apiError(code, response)
            val error = json.optString("error")
            if (error.isNotBlank() && error !in setOf("authorization_pending", "slow_down", "access_denied", "expired_token", "token_expired")) {
                throw IOException(json.optString("error_description", error))
            }
            return json
        } finally { connection.disconnect() }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
    }

    private fun apiError(code: Int, body: String): IOException {
        val detail = try { JSONObject(body).optString("message") } catch (_: Exception) { "" }
        return IOException(when (code) {
            401 -> "GitHub authorization expired. Connect GitHub again."
            403 -> "GitHub denied access. Check the App's Contents: Read-only permission and repository installation."
            404 -> "The configured games repository or its games folder was not found, or the GitHub App cannot access it."
            else -> detail.ifBlank { "GitHub API request failed (HTTP $code)" }
        })
    }

    private fun validateGamePath(path: String) {
        val segments = path.split('/')
        require(path.length in 1..220 && segments.all { it.isNotBlank() && it != "." && it != ".." && it.none { c -> c == '\\' || c == '\u0000' } }) {
            "Invalid game path"
        }
    }

    private fun encodePathSegment(value: String) = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    private fun encodePath(value: String) = value.split('/').joinToString("/") { encodePathSegment(it) }
    private fun formEncode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun isAllowedDownloadHost(host: String) = host.equals("api.github.com", true) ||
        host.equals("raw.githubusercontent.com", true) || host.equals("codeload.github.com", true) ||
        host.endsWith(".githubusercontent.com", true)

    companion object {
        private const val DEVICE_CODE_URL = "https://github.com/login/device/code"
        private const val TOKEN_URL = "https://github.com/login/oauth/access_token"
        private const val DEVICE_GRANT = "urn:ietf:params:oauth:grant-type:device_code"
        private const val GITHUB_API_VERSION = "2022-11-28"
        private const val USER_AGENT = "DollXRStore-Android"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val DOWNLOAD_READ_TIMEOUT_MS = 120_000
        private const val TOKEN_REFRESH_MARGIN_MS = 60_000L
        private const val MAX_IMAGE_BYTES = 768 * 1024
        private const val MAX_DESCRIPTION_CHARS = 700
        private const val MAX_GAME_FILE_BYTES = 100L * 1024L * 1024L
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
