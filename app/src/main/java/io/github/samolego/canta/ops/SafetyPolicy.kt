package io.github.samolego.canta.ops

data class SafetyWarning(val kind: String, val detail: String, val packageName: String = "", val userId: Int = 0) {
    val key: String get() = "$userId:$packageName:$kind:$detail"
}
data class SafetyAssessment(val protected: Boolean = false, val warnings: List<SafetyWarning> = emptyList(), val error: String? = null) {
    fun permits(approved: Set<String>): Boolean = !protected && error == null && warnings.all { it.key in approved }
}
internal data class SafetySnapshot(
    val protectedPackages: Set<String> = emptySet(),
    val roles: Map<String, Set<String>> = emptyMap(),
    val keyboards: Set<String> = emptySet(),
    val admins: Set<String> = emptySet(),
    val installedPackages: Set<String> = emptySet(),
)
internal object SafetyPolicy {
    fun protectedPackages(self: String): Set<String> = setOf(self, "moe.shizuku.privileged.api",
        "com.android.shell", "android", "com.android.systemui", "com.android.settings",
        "com.android.packageinstaller", "com.google.android.packageinstaller",
        "com.android.permissioncontroller", "com.google.android.permissioncontroller")

    fun keyboardPackages(default: String, enabled: String): Set<String> =
        (listOf(default) + enabled.split(':')).filter { '/' in it }.map { it.substringBefore('/') }.toSet()

    fun assess(name: String, snapshot: SafetySnapshot, neededBy: List<String>, userId: Int = 0): SafetyAssessment = SafetyAssessment(
        protected = name in snapshot.protectedPackages,
        warnings = buildList {
            snapshot.roles.filterValues { name in it }.keys.sorted().forEach { add(SafetyWarning("role", it, name, userId)) }
            if (name in snapshot.keyboards) add(SafetyWarning("keyboard", name, name, userId))
            if (name in snapshot.admins) add(SafetyWarning("admin", name, name, userId))
            neededBy.distinct().filter { it in snapshot.installedPackages }.sorted().forEach { add(SafetyWarning("dependent", it, name, userId)) }
        },
    )
}
