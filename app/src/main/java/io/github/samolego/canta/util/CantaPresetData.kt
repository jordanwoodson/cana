package io.github.samolego.canta.util

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class CantaPresetData(
    val name: String,
    val description: String,
    val createdDate: Long,
    val apps: Set<String>,
    val version: String = "1.0",
    val uuid: String = "",
    val lockdown: List<LockdownSettings> = emptyList(),
    val profileKind: String? = null,
) : Parcelable

@Parcelize
data class LockdownSettings(val packageName: String, val revokePermissions: Boolean = false,
    val restrictBackground: Boolean = false, val denyMetered: Boolean = false, val blockNetwork: Boolean = false,
) : Parcelable
