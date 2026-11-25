package io.github.samolego.canta.data

import android.annotation.SuppressLint
import android.content.Context
import androidx.datastore.core.DataMigration
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.DataStore
import androidx.datastore.dataStore
import io.github.samolego.canta.data.proto.AppSettings
import io.github.samolego.canta.util.BloatListSource
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

internal val settingsCorruptionHandler = ReplaceFileCorruptionHandler {
    AppSettingsSerializer.defaultValue.toBuilder().setAuthEnabled(true).setSettingsRecoveryRequired(true).build()
}

private val Context.dataStore: DataStore<AppSettings> by
        dataStore(fileName = "app_settings.pb", serializer = AppSettingsSerializer,
            corruptionHandler = settingsCorruptionHandler,
            produceMigrations = { listOf(ListSourceMigration) })

internal object ListSourceMigration : DataMigration<AppSettings> {
    override suspend fun shouldMigrate(currentData: AppSettings) = !currentData.listSourceMigrated
    override suspend fun migrate(currentData: AppSettings): AppSettings {
        val url = BloatListSource.migrateLegacyUrl(currentData.bloatListUrl)
        return currentData.toBuilder().setListSourceMigrated(true).setBloatListUrl(url).apply {
            if (url != currentData.bloatListUrl) clearBloatCacheUrl().clearBloatEtag().clearBloatLastCheckedMs()
        }.build()
    }
    override suspend fun cleanUp() = Unit
}

class SettingsStore internal constructor(private val dataStore: DataStore<AppSettings>) {
    val autoUpdateBloatListFlow = dataStore.data.map { it.autoUpdateBloatList }
    val confirmBeforeUninstallFlow = dataStore.data.map { it.confirmBeforeUninstall }
    val disableRiskDialogFlow = dataStore.data.map { it.disableRiskDialog }
    val bloatListUrlFlow = dataStore.data.map {
        BloatListSource.effectiveUrl(it.bloatListUrl)
    }
    val bloatUnmeteredOnlyFlow = dataStore.data.map { it.bloatUnmeteredOnly }
    val allowUnsafeUninstallsFlow = dataStore.data.map { it.allowUnsafeUninstalls }

    val recoveryRequired = dataStore.data.map { it.settingsRecoveryRequired }
    suspend fun acknowledgeSettingsRecovery() {
        dataStore.updateData { settings ->
            if (settings.settingsRecoveryRequired) settings.toBuilder().setAuthEnabled(true).clearSettingsRecoveryRequired().build()
            else settings
        }
    }

    val authEnabledFlow = dataStore.data.map { it.authEnabled }
    val trackerListUrlFlow = dataStore.data.map { it.trackerListUrl }

    suspend fun setTrackerListUrl(url: String) {
        dataStore.updateData { it.toBuilder().setTrackerListUrl(url).build() }
    }


    suspend fun setAutoUpdateBloatList(autoUpdate: Boolean) {
        dataStore.updateData { it.toBuilder().setAutoUpdateBloatList(autoUpdate).build() }
    }

    suspend fun setConfirmBeforeUninstall(needsConfirm: Boolean) {
        dataStore.updateData { it.toBuilder().setConfirmBeforeUninstall(needsConfirm).build() }
    }

    suspend fun setDisableRiskDialog(disable: Boolean) {
        dataStore.updateData { it.toBuilder().setDisableRiskDialog(disable).build() }
    }

    suspend fun setBloatListUrl(url: String) {
        dataStore.updateData {
            it.toBuilder().setBloatListUrl(url.trim()).clearBloatEtag()
                .clearBloatCacheUrl().clearBloatLastCheckedMs().build()
        }
    }

    suspend fun snapshot(): AppSettings = dataStore.data.first()

    suspend fun setBloatUnmeteredOnly(value: Boolean) {
        dataStore.updateData { it.toBuilder().setBloatUnmeteredOnly(value).build() }
    }

    suspend fun setBloatCacheMetadata(url: String, etag: String, checkedMs: Long) {
        dataStore.updateData {
            if (BloatListSource.effectiveUrl(it.bloatListUrl) != url) it
            else it.toBuilder().setBloatCacheUrl(url).setBloatEtag(etag)
                .setBloatLastCheckedMs(checkedMs).build()
        }
    }

    suspend fun setAllowUnsafeUninstalls(allow: Boolean) {
        dataStore.updateData { it.toBuilder().setAllowUnsafeUninstalls(allow).build() }
    }

    suspend fun setAuthEnabled(enabled: Boolean) {
        dataStore.updateData { it.toBuilder().setAuthEnabled(enabled).build() }
    }



    companion object {
        @SuppressLint("StaticFieldLeak") @Volatile private var INSTANCE: SettingsStore? = null

        fun initialize(appContext: Context) {
            synchronized(this) {
                if (INSTANCE == null) {
                    INSTANCE = SettingsStore(appContext.dataStore)
                }
            }
        }

        fun getInstance(): SettingsStore {
            return INSTANCE
                    ?: throw IllegalStateException(
                            "SettingsStore has not been initialized. Call initialize() in MyApplication.onCreate()."
                    )
        }
    }
}
