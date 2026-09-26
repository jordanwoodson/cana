package io.github.samolego.canta.ops

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import io.github.samolego.canta.MainActivity
import io.github.samolego.canta.R
import io.github.samolego.canta.data.HistoryStore
import io.github.samolego.canta.data.ManagementStore
import io.github.samolego.canta.util.LogUtils
import io.github.samolego.canta.util.apps.UserProfile
import io.github.samolego.canta.util.shizuku.ShizukuPackageInstallerUtils
import io.github.samolego.canta.util.shizuku.ShizukuPermission
import io.github.samolego.canta.util.shizuku.ShizukuUserUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class OtaRepository(private val context: Context, val store: ManagementStore, private val history: HistoryStore) {
    private val mutex = Mutex()
    suspend fun check() = withContext(Dispatchers.IO) { mutex.withLock {
        val users = if (ShizukuPermission.isCantaAuthorized()) ShizukuUserUtils.getUsers().map { it.id }
            else listOf(UserProfile.currentUserId)
        val inventories = users.mapNotNull { user ->
            try {
                val packages = if (user == UserProfile.currentUserId)
                    context.packageManager.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                else ShizukuPackageInstallerUtils.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES, user)
                // A locked/inaccessible profile must keep its old baseline until it can be queried.
                check(packages.isNotEmpty()) { "No package inventory for user $user" }
                PackageInventory(user, packages.filter { it.applicationInfo!!.flags and ApplicationInfo.FLAG_INSTALLED != 0 }.map { it.packageName }.toSet(),
                    packages.filter { it.applicationInfo!!.flags and ApplicationInfo.FLAG_SYSTEM != 0 }.map { it.packageName }.toSet())
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { LogUtils.e("OtaRepository", "Cannot inventory user $user", e); null }
        }
        val updated = store.observe(Build.FINGERPRINT, inventories, history.allRecords())
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (updated.pendingCount == 0) { notifications.cancel(NOTIFICATION_ID); return@withLock }
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withLock
        if (!notifications.areNotificationsEnabled()) return@withLock
        val signature = updated.pendingList.joinToString("|") { "${it.userId}:${it.packageName}:${it.fingerprint}:${it.returned}" }
        if (signature == updated.notifiedChanges) return@withLock
        notifications.createNotificationChannel(NotificationChannel(CHANNEL, context.getString(R.string.ota_title), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, NOTIFICATION_ID, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        notifications.notify(NOTIFICATION_ID, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground).setContentTitle(context.getString(R.string.ota_title))
            .setContentText(context.getString(R.string.ota_summary, updated.pendingCount)).setContentIntent(open).setAutoCancel(true).build())
        store.notified(signature)
    } }
    companion object { private const val CHANNEL = "system_updates"; private const val NOTIFICATION_ID = 51740 }
}
