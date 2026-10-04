package io.github.samolego.canta.ops

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import io.github.samolego.canta.extension.getAllPackagesInfo
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.BloatListRepository
import io.github.samolego.canta.util.apps.AppInfo
import kotlinx.coroutines.*
import org.json.JSONObject

data class InventoryState(
    val apps: List<AppInfo> = emptyList(),
    val loading: Boolean = false,
    val loadingMetadata: Boolean = false,
    val usageAvailable: Boolean = false,
    val stale: Boolean = true,
    val error: String? = null,
    val loaded: Boolean = false,
)

/** A single inventory per profile. UI filters and selections never alter these snapshots. */
class InventoryRepository(
    context: Context,
    private val scope: CoroutineScope,
    private val metadataLoader: (suspend (Context, Boolean) -> JSONObject)? = null,
) {
    private val context = context.applicationContext
    private val states = mutableStateMapOf<Int, InventoryState>()
    private val generations = mutableMapOf<Int, Long>()
    private val invalidations = mutableMapOf<Int, Job>()
    fun state(userId: Int): InventoryState = states[userId] ?: InventoryState()

    /** Fresh package state for previews/comparison, without waiting on recommendation downloads. */
    suspend fun snapshot(userId: Int): InventoryState {
        refresh(userId, refreshMetadata = false)
        return withContext(Dispatchers.Main.immediate) { state(userId) }
    }

    /** Marks inventories stale; with [refresh] a reload follows shortly (a full package scan). */
    fun invalidate(userId: Int? = null, refresh: Boolean = true) {
        scope.launch {
            val users = userId?.let(::listOf) ?: states.keys.toList()
            users.forEach { user ->
                val old = state(user)
                states[user] = old.copy(stale = true)
                if (old.loaded && refresh) {
                    invalidations[user]?.cancel()
                    invalidations[user] = launch { delay(150); refresh(user, refreshMetadata = false) }
                }
            }
        }
    }

    suspend fun refresh(userId: Int, forceMetadata: Boolean = false, refreshMetadata: Boolean = true) = withContext(Dispatchers.Main.immediate) {
        val generation = (generations[userId] ?: 0) + 1
        generations[userId] = generation
        states[userId] = state(userId).copy(loading = true, error = null, stale = true)
        fun publish(update: (InventoryState) -> InventoryState) {
            if (generations[userId] == generation) states[userId] = update(state(userId))
        }
        try {
            val (apps, usageAvailable) = withContext(Dispatchers.IO) {
                val packages = context.packageManager.getAllPackagesInfo(userId)
                val usage = runCatching { UsageRepository(context).lastUsed(userId) }.getOrNull()
                val cached = parseMetadata(BloatListRepository(context).cached())
                val decorated = packages.map { app -> app.copy(bloatData = cached[app.packageName],
                    lastUsed = if (usage == null || app.isUninstalled) null else usage[app.packageName] ?: 0L) }
                applyProfileGuidance(decorated, userId, cached) to (usage != null)
            }
            publish { it.copy(apps = apps, loading = false, loadingMetadata = refreshMetadata, loaded = true,
                stale = refreshMetadata, usageAvailable = usageAvailable) }
            if (!refreshMetadata) return@withContext
            val metadata = withContext(Dispatchers.IO) {
                parseMetadata(metadataLoader?.invoke(context, forceMetadata) ?: BloatListRepository(context).load(forceMetadata))
            }
            val currentApps = state(userId).apps
            val decorated = withContext(Dispatchers.IO) {
                applyProfileGuidance(currentApps.map { it.copy(bloatData = metadata[it.packageName]) }, userId, metadata)
            }
            publish { it.copy(apps = decorated, stale = false) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { publish { it.copy(error = (e.cause ?: e).message ?: e.toString()) } }
        finally { publish { it.copy(loading = false, loadingMetadata = false) } }
    }

    private suspend fun applyProfileGuidance(apps: List<AppInfo>, userId: Int, metadata: Map<String, BloatData>): List<AppInfo> {
        val services = CanaServices.getInstance()
        val kind = try { io.github.samolego.canta.util.shizuku.ShizukuUserUtils.getUsers().firstOrNull { it.id == userId }?.kind }
            catch (e: Exception) { services.profiles.find(userId)?.kind }
        if (kind != io.github.samolego.canta.util.apps.UserProfile.Kind.WORK) return apps
        val installed = apps.filterNot { it.isUninstalled }.map { it.packageName }.toSet()
        val assessments = services.safety.inspect(apps.map { it.packageName }, userId)
        return apps.map { app -> app.copy(profileGuidance = WorkProfileGuidance.assess(app.packageName, kind,
            app.bloatData, assessments[app.packageName] ?: SafetyAssessment(error = "Unavailable"),
            WorkProfileGuidance.installedDependents(app.packageName, metadata, installed))) }
    }

    private fun parseMetadata(json: JSONObject): Map<String, BloatData> = buildMap {
        for (key in json.keys()) json.optJSONObject(key)?.let { put(key, BloatData.fromJson(it)) }
        put(io.github.samolego.canta.packageName, BloatData(null,
            io.github.samolego.canta.util.optionalTextArgument(context.getString(io.github.samolego.canta.R.string.canta_description),
                "Muntashir Al-Islam and Android Debloat List contributors (https://github.com/MuntashirAkon/android-debloat-list)"), null))
    }
}
