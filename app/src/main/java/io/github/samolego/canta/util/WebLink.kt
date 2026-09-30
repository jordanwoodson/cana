package io.github.samolego.canta.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.samolego.canta.R

/** Devices with their browser removed should keep Cana open when a reference link is tapped. */
fun Context.openWebLink(url: String) {
    try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    catch (_: Exception) { Toast.makeText(this, R.string.open_link_failed, Toast.LENGTH_LONG).show() }
}
