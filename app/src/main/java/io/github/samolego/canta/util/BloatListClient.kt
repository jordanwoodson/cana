package io.github.samolego.canta.util

import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal class BloatListClient(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000,
) {
    data class Response(val data: JSONObject?, val etag: String)

    fun fetch(url: String, etag: String): Response {
        val address = URL(url)
        require(address.protocol in setOf("https", "http")) { "List URL must use HTTP or HTTPS" }
        val connection = address.openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/zip, application/json")
            if (etag.isNotEmpty()) connection.setRequestProperty("If-None-Match", etag)
            val receivedEtag = connection.getHeaderField("ETag")
            return when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> Response(null, receivedEtag ?: etag)
                HttpURLConnection.HTTP_OK -> {
                    val bytes = connection.inputStream.use { it.readBloatBytes() }
                    val data = if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4b.toByte())
                        parseAndroidDebloatArchive(bytes) else parseBloatList(bytes.toString(Charsets.UTF_8))
                    Response(data, receivedEtag.orEmpty())
                }
                else -> throw IOException("Bloat list request failed: HTTP $code")
            }
        } finally {
            connection.disconnect()
        }
    }
}

internal fun parseBloatList(text: String): JSONObject {
    val json = JSONObject(text)
    require(json.length() > 0) { "Bloat list is empty" }
    for (key in json.keys()) {
        require(json.optJSONObject(key) != null) { "Invalid bloat list entry: $key" }
    }
    return json
}

internal class BloatListCache(private val file: File, private val fallback: () -> String) {
    data class Cached(val data: JSONObject, val fromCache: Boolean)
    data class Updated(val data: JSONObject, val etag: String)

    fun load(): Cached {
        if (file.isFile) {
            try {
                return Cached(parseBloatList(file.readText()), true)
            } catch (_: Exception) {
                // A corrupt or interrupted legacy cache must not defeat the bundled fallback.
            }
        }
        return Cached(parseBloatList(fallback()), false)
    }

    fun refresh(client: BloatListClient, url: String, etag: String): Updated {
        val current = load()
        val response = client.fetch(url, if (current.fromCache) etag else "")
        val data = response.data
        if (data == null) {
            check(current.fromCache) { "Server returned 304 without a valid cached list" }
            return Updated(current.data, response.etag)
        }
        file.parentFile?.mkdirs()
        val temporary = File(file.path + ".tmp")
        try {
            temporary.writeText(data.toString())
            check(temporary.renameTo(file)) { "Could not replace bloat list cache" }
        } finally {
            temporary.delete()
        }
        return Updated(data, response.etag)
    }
}
