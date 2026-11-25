package io.github.samolego.canta.ui.component

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.PackageStateCsv
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Saves every package of the selected profile as CSV. Registered outside the menu so the result outlives it. */
@Composable
fun rememberPackageCsvExport(selectedUserId: Int, selectedKind: UserProfile.Kind?): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var exportUser by rememberSaveable { mutableStateOf<Int?>(null) }
    var exportKind by rememberSaveable { mutableStateOf<UserProfile.Kind?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        val userId = exportUser
        val kind = exportKind
        exportUser = null
        exportKind = null
        if (uri != null && userId != null) scope.launch {
            val services = CanaServices.getInstance()
            val message = try {
                val inventory = services.inventory.snapshot(userId)
                val apps = inventory.apps
                check(inventory.loaded && !inventory.stale && inventory.error == null && apps.isNotEmpty()) {
                    inventory.error ?: resources.getString(R.string.package_csv_empty)
                }
                val scans = apps.associate { it.packageName to services.trackerScans.result(it) }
                val history = services.history.allRecords()
                withContext(Dispatchers.IO) {
                    val csv = PackageStateCsv.generate(apps, userId, kind,
                        trackerScan = { scans[it.packageName] },
                        installer = { name -> installer(context.packageManager, name, userId) },
                        history = history)
                    checkNotNull(context.contentResolver.openOutputStream(uri, "wt")).bufferedWriter().use { it.write(csv) }
                }
                resources.getQuantityString(R.plurals.package_csv_saved, apps.size, apps.size)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { resources.getString(R.string.package_csv_failed, e.message.orEmpty()) }
            services.uiMessages.post(message)
        }
    }
    return {
        exportUser = selectedUserId
        exportKind = selectedKind
        picker.launch(PackageStateCsv.fileName(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
            selectedUserId, LocalDate.now()))
    }
}

private fun installer(packageManager: PackageManager, packageName: String, userId: Int): String? = try {
    if (userId != UserProfile.currentUserId) {
        io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils.installSource(packageName, userId)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        packageManager.getInstallSourceInfo(packageName).installingPackageName
    } else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName)
} catch (_: Exception) { null }
