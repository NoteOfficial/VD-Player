package com.tkofficial.videodownloder.core

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.R
import com.tkofficial.videodownloder.models.AppLanguage
import java.util.Locale

object LocaleHelper {

    fun buildLocalizedConfiguration(baseConfig: Configuration, language: AppLanguage): Configuration {
        val locale = Locale.forLanguageTag(language.tag)
        Locale.setDefault(locale)
        return Configuration(baseConfig).apply {
            setLocale(locale)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                setLocales(LocaleList(locale))
            }
            setLayoutDirection(locale)
        }
    }

    /**
     * Wraps the given [baseContext] using AppCompat's [ContextThemeWrapper] with an override
     * configuration so Jetpack Compose [stringResource] and [LocalContext] resolve strings for
     * [language] immediately while preserving the underlying Activity chain via [findActivity].
     */
    fun wrapContext(baseContext: Context, language: AppLanguage): Context {
        val config = buildLocalizedConfiguration(baseContext.resources.configuration, language)
        return ContextThemeWrapper(baseContext, R.style.Theme_DownloaderVA).apply {
            applyOverrideConfiguration(config)
        }
    }

    fun getLocalizedString(
        context: Context,
        language: AppLanguage,
        @StringRes resId: Int,
        vararg formatArgs: Any
    ): String {
        val config = buildLocalizedConfiguration(context.resources.configuration, language)
        val localizedRes = context.createConfigurationContext(config).resources
        return if (formatArgs.isEmpty()) {
            localizedRes.getString(resId)
        } else {
            localizedRes.getString(resId, *formatArgs)
        }
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun audioReadPermissionName(): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            audioReadPermissionName()
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasCameraPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }
}

tailrec fun Context.findActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

fun Context.openAppDetailsSettings() {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { startActivity(intent) }
}
