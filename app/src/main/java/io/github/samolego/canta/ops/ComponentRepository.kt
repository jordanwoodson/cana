package io.github.samolego.canta.ops

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.ComponentInfo
import android.content.pm.PackageManager
import io.github.samolego.canta.util.TrackerCatalog
import io.github.samolego.canta.util.TrackerRepository
import io.github.samolego.canta.util.TrackerSignatures
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

data class AppComponent(val name: ComponentName, val kind: String, val enabledSetting: Int,
    val manifestEnabled: Boolean, val trackers: List<String>) {
    val enabled: Boolean get() = when (enabledSetting) { 0 -> manifestEnabled; 1 -> true; else -> false }
}
data class ComponentCatalog(val components: List<AppComponent>, val editable: Boolean, val trackers: TrackerCatalog)

class ComponentRepository(private val trackers: TrackerRepository) {
    suspend fun load(packageName: String, userId: Int): ComponentCatalog = withContext(Dispatchers.IO) {
        val flags = PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or
            PackageManager.GET_PROVIDERS or PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_UNINSTALLED_PACKAGES
        val info = checkNotNull(ShizukuPackageInstallerUtils.getPackageInfo(packageName, flags, userId)) { "Package unavailable" }
        val signatures = trackers.load()
        val components = buildList {
            fun collect(entries: Array<out ComponentInfo>?, kind: String) {
                entries.orEmpty().forEach { entry ->
                    val component = ComponentName(packageName, entry.name)
                    add(AppComponent(component, kind, ShizukuPackageInstallerUtils.componentEnabledSetting(component, userId),
                        entry.enabled, TrackerSignatures.matches(entry.name, signatures.signatures)))
                }
            }
            collect(info.activities, "activity"); collect(info.services, "service")
            collect(info.receivers, "receiver"); collect(info.providers, "provider")
        }.sortedWith(compareBy<AppComponent> { it.trackers.isEmpty() }.thenBy { it.kind }.thenBy { it.name.className })
        ComponentCatalog(components, Shizuku.getUid() != 2000 || (info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_TEST_ONLY != 0, signatures)
    }
}
