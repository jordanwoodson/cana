package io.github.samolego.canta.util

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class BloatListClientTest {
    private val bundled = """{"android":{"list":"Aosp","removal":"Unsafe"}}"""
    private val downloaded = """{"com.example":{"list":"Google","removal":"Recommended"}}"""

    @Test fun downloadUsesEtagThenKeepsCacheOnNotModified() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(downloaded).setHeader("ETag", "\"v1\""))
            server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"v1\""))
            val dir = Files.createTempDirectory("uad-test").toFile()
            try {
                val cache = BloatListCache(dir.resolve("list.json")) { bundled }
                val client = BloatListClient()
                val url = server.url("/list").toString()
                val first = cache.refresh(client, url, "")
                assertTrue(first.data.has("com.example"))
                assertEquals("\"v1\"", first.etag)
                val modified = dir.resolve("list.json").lastModified()
                val second = cache.refresh(client, url, first.etag)
                assertTrue(second.data.has("com.example"))
                assertEquals(modified, dir.resolve("list.json").lastModified())
                assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
                assertEquals("\"v1\"", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun missingAndCorruptCacheUseBundledList() {
        val dir = Files.createTempDirectory("uad-test").toFile()
        try {
            val file = dir.resolve("list.json")
            val cache = BloatListCache(file) { bundled }
            assertTrue(cache.load().data.has("android"))
            assertFalse(cache.load().fromCache)
            file.writeText("{broken")
            assertTrue(cache.load().data.has("android"))
            file.writeText(downloaded)
            assertTrue(cache.load().fromCache)
            assertTrue(cache.load().data.has("com.example"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun invalidDownloadCannotReplaceGoodCache() {
        for (body in listOf("{broken", "{}", """{"error":"not a list"}""")) {
            MockWebServer().use { server ->
                server.enqueue(MockResponse().setBody(body))
                val dir = Files.createTempDirectory("uad-test").toFile()
                try {
                    val file = dir.resolve("list.json").apply { writeText(downloaded) }
                    val cache = BloatListCache(file) { bundled }
                    assertThrows(Exception::class.java) {
                        cache.refresh(BloatListClient(), server.url("/list").toString(), "")
                    }
                    assertEquals(downloaded, file.readText())
                } finally { dir.deleteRecursively() }
            }
        }
    }

    @Test fun lostCacheDoesNotSendStaleEtag() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(503))
            val dir = Files.createTempDirectory("uad-test").toFile()
            try {
                val cache = BloatListCache(dir.resolve("list.json")) { bundled }
                assertThrows(Exception::class.java) {
                    cache.refresh(BloatListClient(), server.url("/list").toString(), "\"old\"")
                }
                assertNull(server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
                assertTrue(cache.load().data.has("android"))
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun delayedResponseIsBoundedByReadTimeout() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(downloaded).setBodyDelay(300, TimeUnit.MILLISECONDS))
            assertThrows(java.net.SocketTimeoutException::class.java) {
                BloatListClient(readTimeoutMs = 50).fetch(server.url("/list").toString(), "")
            }
        }
    }

    @Test fun downloadsTheUpstreamArchiveAndCachesTheConvertedList() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(AndroidDebloatListTest().fixture()))
                .setHeader("Content-Type", "application/zip").setHeader("ETag", "\"adl-v1\""))
            server.enqueue(MockResponse().setResponseCode(304))
            val dir = Files.createTempDirectory("adl-test").toFile()
            try {
                val file = dir.resolve("list.json")
                val cache = BloatListCache(file) { bundled }
                val url = server.url("/master.zip").toString()
                val first = cache.refresh(BloatListClient(), url, "")
                val data = BloatData.fromJson(first.data.getJSONObject("com.example.browser"))
                assertEquals("Open Browser", data.alternatives.single().label)
                assertEquals(first.data.toString(), parseBloatList(file.readText()).toString())
                val second = cache.refresh(BloatListClient(), url, first.etag)
                assertEquals(first.data.toString(), second.data.toString())
                server.takeRequest(1, TimeUnit.SECONDS)
                assertEquals("\"adl-v1\"", server.takeRequest(1, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun incompleteUpstreamArchiveCannotReplaceTheCache() {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(okio.Buffer().write(
                AndroidDebloatListTest().archive(mapOf("google.json" to "[]")))))
            val dir = Files.createTempDirectory("adl-test").toFile()
            try {
                val file = dir.resolve("list.json").apply { writeText(downloaded) }
                assertThrows(Exception::class.java) {
                    BloatListCache(file) { bundled }.refresh(BloatListClient(), server.url("/master.zip").toString(), "")
                }
                assertEquals(downloaded, file.readText())
            } finally { dir.deleteRecursively() }
        }
    }

    @Test fun bundledListParsesWithAllSupportedMetadata() {
        val json = parseAndroidDebloatArchive(java.io.File("src/main/assets/android-debloat-list.zip").readBytes())
        val entries = json.keys().asSequence().map { BloatData.fromJson(json.getJSONObject(it)) }.toList()
        assertTrue(entries.isNotEmpty())
        assertTrue(entries.all { it.installData != null })
        assertTrue(entries.any { it.alternatives.any { app -> app.label == "Open Camera" } })
        assertTrue(entries.any { it.neededBy.isNotEmpty() })
    }
}
