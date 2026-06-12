package com.chronosflow.wear

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.wear.remote.interactions.RemoteActivityHelper

/**
 * Opens the ChronosFlow phone app from the watch via [RemoteActivityHelper]. Used as the escape
 * hatch for anything the wrist deliberately doesn't show — most importantly the lists that are
 * dropped from the mirror when "redact sensitive titles" is on.
 *
 * Fires an `ACTION_VIEW` for the `chronosflow://open` deep link the phone's MainActivity handles.
 * Best-effort: there may be no paired phone, or no Google Play services.
 */
object PhoneHandoff {

    private const val DEEP_LINK = "chronosflow://open"

    fun open(context: Context) {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW)
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .setData(Uri.parse(DEEP_LINK))
            RemoteActivityHelper(context.applicationContext).startRemoteActivity(intent)
        }
    }
}
