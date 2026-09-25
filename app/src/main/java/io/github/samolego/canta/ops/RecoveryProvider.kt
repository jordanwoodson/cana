package io.github.samolego.canta.ops

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import kotlinx.coroutines.runBlocking

/** Shell-only, narrowly scoped recovery endpoint. DUMP is a platform signature permission.
 * ContentProvider calls remain alive while the journal finishes an in-flight reconciliation.
 */
class RecoveryProvider : ContentProvider() {
    override fun onCreate(): Boolean = true
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        checkNotNull(context).enforceCallingPermission("android.permission.DUMP", "Shell recovery only")
        require(method in setOf("network-desired", "metered-desired") && extras != null)
        val pkg = requireNotNull(extras.getString("package"))
        val user = extras.getInt("user", -1)
        val appId = extras.getInt("appId", -1)
        PrivacyPolicy.uid(user, appId)
        require(Regex("[A-Za-z][A-Za-z0-9_.]*").matches(pkg))
        val blocked = extras.getBoolean("blocked")
        val result = runBlocking { CanaServices.getInstance().privacy.restoreDesired(pkg, user, appId, blocked,
            if (method == "metered-desired") PrivacyAction.METERED else PrivacyAction.NETWORK) }
        return Bundle().apply { putBoolean("restored", result.success); putString("message", result.message) }
    }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
}
