package io.github.samolego.canta.ops

import android.os.Parcelable
import androidx.annotation.Keep
import kotlinx.parcelize.Parcelize

@Keep
@Parcelize
data class ShellResult(val exitCode: Int, val stdout: String, val stderr: String) : Parcelable {
    val success: Boolean get() = exitCode == 0
    val message: String get() = stderr.ifBlank { stdout }.trim()
}
