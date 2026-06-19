package com.ChronosFlow.VBCR.core.notifications

import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ChronosFlow.VBCR.core.domain.model.AppLaunchTarget
import com.ChronosFlow.VBCR.core.domain.model.isComponentLaunchValue
import com.ChronosFlow.VBCR.core.domain.model.isPackageLaunchValue
import com.ChronosFlow.VBCR.core.domain.model.normalizeAppLaunchValue
import com.ChronosFlow.VBCR.core.domain.model.parseAppLaunchComponent

fun buildAppLaunchIntent(value: String): Intent? {
    val normalized = normalizeAppLaunchValue(value) ?: return null
    val component = parseAppLaunchComponent(normalized)
    val intent = when {
        component != null -> Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(ComponentName(component.packageName, component.className))
        normalized.startsWith("intent:", ignoreCase = true) ->
            runCatching { Intent.parseUri(normalized, Intent.URI_INTENT_SCHEME) }.getOrNull()
        isPackageLaunchValue(normalized) -> Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setPackage(normalized)
        else -> Intent(Intent.ACTION_VIEW, Uri.parse(normalized))
    } ?: return null

    return intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

fun canLaunchAppTarget(context: Context, target: AppLaunchTarget): Boolean =
    canLaunchAppValue(context, target.value)

fun canLaunchAppValue(context: Context, value: String): Boolean {
    val normalized = normalizeAppLaunchValue(value) ?: return false
    if (isPackageLaunchValue(normalized)) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(normalized)
        if (launchIntent != null) return true
    }
    val intent = buildAppLaunchIntent(normalized) ?: return false
    return intent.resolveActivity(context.packageManager) != null ||
        isPackageLaunchValue(normalized) ||
        isComponentLaunchValue(normalized)
}

fun launchAppTarget(context: Context, target: AppLaunchTarget): Boolean =
    launchAppValue(context, target.value)

fun launchAppValue(context: Context, value: String): Boolean {
    val intent = buildAppLaunchIntent(value) ?: return false
    return runCatching {
        context.startActivity(intent)
        true
    }.recoverCatching {
        if (it is ActivityNotFoundException) false else throw it
    }.getOrDefault(false)
}

fun buildAppLaunchPendingIntent(
    context: Context,
    target: AppLaunchTarget,
    requestCode: Int
): PendingIntent? {
    val intent = buildAppLaunchIntent(target.value) ?: return null
    return PendingIntent.getActivity(
        context,
        requestCode,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
