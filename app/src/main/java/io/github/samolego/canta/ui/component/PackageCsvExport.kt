package io.github.samolego.canta.ui.component

import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import io.github.samolego.canta.R
import io.github.samolego.canta.ops.CanaServices
import io.github.samolego.canta.ops.PackageStateCsv
import io.github.samolego.canta.ui.viewmodel.AppListViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Saves every package of the selected profile as CSV. Registered outside the menu so the result outlives it. */
@Composable
fun rememberPackageCsvExport(appListViewModel: AppListViewModel): () -> Unit {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val services = CanaServices.getInstance()
            val message = try {
                val userId = appListViewModel.selectedUserId
                if (appListViewModel.allApps.isEmpty()) appListViewModel.loadInstalled(context.packageManager, context)
                val apps = appListViewModel.allApps
                check(apps.isNotEmpty()) { appListViewModel.loadError ?: resources.getString(R.string.package_csv_empty) }
                val scans = apps.associate { it.packageName to services.trackerScans.result(it) }
                val history = services.history.allRecords()
                withContext(Dispatchers.IO) {
                    val csv = PackageStateCsv.generate(apps, userId, appListViewModel.selectedProfile?.kind,
                        trackerScan = { scans[it.packageName] },
                        installer = { installer(context.packageManager, it) },
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
        picker.launch(PackageStateCsv.fileName(Build.MANUFACTURER, Build.MODEL, Build.VERSION.RELEASE,
            appListViewModel.selectedUserId, LocalDate.now()))
    }
}

private fun installer(packageManager: PackageManager, packageName: String): String? = try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) packageManager.getInstallSourceInfo(packageName).installingPackageName
    else @Suppress("DEPRECATION") packageManager.getInstallerPackageName(packageName)
} catch (_: Exception) { null }
