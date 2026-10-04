package io.github.samolego.canta.ops

import android.os.Parcelable
import io.github.samolego.canta.R
import io.github.samolego.canta.util.BloatData
import io.github.samolego.canta.util.RemovalRecommendation
import io.github.samolego.canta.util.RemovalRecommendation.*
import io.github.samolego.canta.util.apps.UserProfile
import kotlinx.parcelize.Parcelize

enum class WorkNeed(val label: Int) {
    KEEP(R.string.work_need_keep), OPTIONAL(R.string.work_need_optional), REVIEW(R.string.work_need_review),
}

@Parcelize
data class ProfileGuidance(val need: WorkNeed, val recommendation: RemovalRecommendation,
    val reason: Int, val details: List<String> = emptyList()) : Parcelable

/** Work guidance is deliberately separate from the general list, which remains visible in details. */
internal object WorkProfileGuidance {
    fun assess(packageName: String, kind: UserProfile.Kind?, data: BloatData?,
        safety: SafetyAssessment, dependents: List<String> = emptyList()): ProfileGuidance? {
        if (kind != UserProfile.Kind.WORK) return null
        fun guidance(need: WorkNeed, risk: RemovalRecommendation, reason: Int,
            details: List<String> = emptyList()): ProfileGuidance {
            val conservative = when (data?.removal) {
                UNSAFE -> UNSAFE
                EXPERT -> if (risk == UNSAFE) UNSAFE else EXPERT
                ADVANCED -> if (risk == RECOMMENDED) ADVANCED else risk
                else -> risk
            }
            return ProfileGuidance(need, conservative, reason, details)
        }
        if (safety.warnings.any { it.kind == "admin" } || packageName in managers)
            return guidance(WorkNeed.KEEP, UNSAFE, R.string.work_reason_management)
        if (safety.protected) return guidance(WorkNeed.KEEP, UNSAFE, R.string.work_reason_core)
        if (safety.error != null) return guidance(WorkNeed.REVIEW, EXPERT, R.string.work_reason_unavailable)
        val active = safety.warnings.filter { it.kind in setOf("role", "keyboard") }
        if (active.isNotEmpty()) return guidance(WorkNeed.KEEP, EXPERT, R.string.work_reason_active,
            active.map { it.detail }.distinct())
        val neededBy = (dependents + safety.warnings.filter { it.kind == "dependent" }.map { it.detail }).distinct().sorted()
        if (neededBy.isNotEmpty()) return guidance(WorkNeed.KEEP, EXPERT, R.string.work_reason_dependents, neededBy)
        if (packageName in core) return guidance(WorkNeed.KEEP, UNSAFE, R.string.work_reason_core)
        if (packageName in googleServices) return guidance(WorkNeed.KEEP, EXPERT, R.string.work_reason_google)
        if (packageName in webViews) return guidance(WorkNeed.KEEP, EXPERT, R.string.work_reason_webview)
        if (data?.removal in setOf(UNSAFE, EXPERT))
            return guidance(WorkNeed.REVIEW, data!!.removal!!, R.string.work_reason_list_caution)
        if (packageName in personalExtras)
            return guidance(WorkNeed.OPTIONAL, RECOMMENDED, R.string.work_reason_personal)
        if (packageName in productivity)
            return guidance(WorkNeed.REVIEW, ADVANCED, R.string.work_reason_productivity)
        return guidance(WorkNeed.REVIEW, ADVANCED, R.string.work_reason_unknown)
    }

    fun installedDependents(packageName: String, metadata: Map<String, BloatData>, installed: Set<String>): List<String> =
        (metadata[packageName]?.neededBy.orEmpty() + metadata.filter { (name, data) ->
            name in installed && packageName in data.dependencies
        }.keys).filter { it != packageName && it in installed }.distinct().sorted()

    private val managers = setOf("com.android.managedprovisioning", "com.google.android.apps.work.clouddpc",
        "com.google.android.apps.enterprise.dmagent", "com.microsoft.windowsintune.companyportal",
        "net.typeblog.shelter", "com.oasisfeng.island", "com.oasisfeng.island.fdroid")
    private val core = SafetyPolicy.protectedPackages("io.github.jordanwoodson.cana") + setOf(
        "com.android.providers.downloads", "com.android.providers.downloads.ui",
        "com.android.providers.media", "com.android.providers.media.module", "com.google.android.providers.media.module",
        "com.android.externalstorage", "com.android.documentsui", "com.google.android.documentsui",
        "com.android.providers.contacts", "com.android.providers.calendar", "com.android.keychain")
    private val googleServices = setOf("com.google.android.gms", "com.google.android.gsf", "com.android.vending",
        "com.google.android.gsf.login")
    private val webViews = setOf("com.google.android.webview", "com.android.webview", "com.android.chrome")
    private val personalExtras = setOf("com.google.android.youtube", "com.google.android.apps.youtube.music",
        "com.google.android.videos", "com.google.android.music", "com.google.android.play.games",
        "com.google.android.apps.books", "com.google.android.apps.magazines", "com.google.android.apps.photos",
        "com.google.android.apps.wallpaper", "com.android.wallpaper.livepicker", "com.google.android.apps.wellbeing")
    private val productivity = setOf("com.google.android.gm", "com.google.android.calendar",
        "com.google.android.contacts", "com.google.android.apps.docs", "com.google.android.apps.maps",
        "com.android.calendar", "com.android.contacts", "com.android.email", "com.android.camera2",
        "com.google.android.GoogleCamera", "com.google.android.apps.messaging", "com.google.android.dialer")
}
