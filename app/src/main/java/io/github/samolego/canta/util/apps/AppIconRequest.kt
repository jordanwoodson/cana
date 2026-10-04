package io.github.samolego.canta.util.apps

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.UserHandle
import androidx.core.graphics.drawable.toBitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import io.github.samolego.canta.util.LogUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Bound resource loading and rasterization while fast scrolling starts several requests at once.
private val iconDispatcher = Dispatchers.IO.limitedParallelism(4)
private data class AppIconData(val app: AppInfo, val userId: Int, val sizePx: Int)

/** No package lookups or file reads here: this is safe to build during composition. */
internal fun appIconRequest(context: Context, app: AppInfo, userId: Int, sizePx: Int): ImageRequest {
    require(sizePx > 0)
    val info = app.applicationInfo
    val key = listOf("cana-icon-v1", app.packageName, userId, app.versionCode, app.apkIdentity,
        info?.sourceDir, info?.publicSourceDir, info?.icon, info?.flags, info?.enabled,
        app.isUninstalled, sizePx, context.resources.configuration.toString()).joinToString("\u0000")
    return ImageRequest.Builder(context)
        .data(AppIconData(app, userId, sizePx))
        .memoryCacheKey(key)
        // An unreadable APK has no trustworthy freshness key; retry it on the next request.
        .memoryCachePolicy(if (app.apkIdentity.isEmpty()) CachePolicy.DISABLED else CachePolicy.ENABLED)
        .fetcherFactory(AppIconFetcher.Factory)
        .fetcherCoroutineContext(iconDispatcher)
        .size(sizePx)
        .crossfade(true)
        .build()
}

private class AppIconFetcher(private val data: AppIconData, private val options: Options) : Fetcher {
    override suspend fun fetch(): ImageFetchResult = withContext(iconDispatcher) {
        try {
            val pm = options.context.packageManager
            // Never substitute a name lookup in Cana's own profile for a missing target-profile record.
            val info = requireNotNull(data.app.applicationInfo) { "Application information unavailable" }
            val raw = pm.getApplicationIcon(info)
            val bitmap = raw.toBitmap(data.sizePx, data.sizePx)
            val rendered = if (data.userId == UserProfile.currentUserId) bitmap else {
                // Android's badger requires intrinsic dimensions and may modify mutable bitmaps.
                // Give it a sized private copy so badging cannot alter another profile's icon.
                val copy = bitmap.copy(Bitmap.Config.ARGB_8888, true).apply {
                    density = options.context.resources.displayMetrics.densityDpi
                }
                pm.getUserBadgedIcon(BitmapDrawable(options.context.resources, copy),
                    UserHandle.getUserHandleForUid(data.userId * 100_000)).toBitmap(data.sizePx, data.sizePx)
            }
            ImageFetchResult(rendered.asImage(),
                isSampled = false, dataSource = DataSource.DISK)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogUtils.w("AppIcon", "Cannot load ${data.app.packageName} icon for profile ${data.userId}: ${e.message}")
            throw e // Coil displays the fallback and does not cache failed requests.
        }
    }

    object Factory : Fetcher.Factory<AppIconData> {
        override fun create(data: AppIconData, options: Options, imageLoader: ImageLoader): Fetcher =
            AppIconFetcher(data, options)
    }
}
