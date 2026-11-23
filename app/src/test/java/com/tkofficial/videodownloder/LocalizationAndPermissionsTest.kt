package com.tkofficial.videodownloder

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.test.core.app.ApplicationProvider
import com.example.R
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.core.LocaleHelper
import com.tkofficial.videodownloder.core.findActivity
import com.tkofficial.videodownloder.models.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalizationAndPermissionsTest {

    @Test
    fun `all four locales resolve translated strings cleanly without missing translations`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val enTelegram = LocaleHelper.getLocalizedString(context, AppLanguage.ENGLISH, R.string.btn_open_telegram)
        val bnTelegram = LocaleHelper.getLocalizedString(context, AppLanguage.BENGALI, R.string.btn_open_telegram)
        val arTelegram = LocaleHelper.getLocalizedString(context, AppLanguage.ARABIC, R.string.btn_open_telegram)
        val hiTelegram = LocaleHelper.getLocalizedString(context, AppLanguage.HINDI, R.string.btn_open_telegram)

        assertEquals("Telegram", enTelegram)
        assertNotEquals(enTelegram, bnTelegram)
        assertNotEquals(enTelegram, arTelegram)
        assertNotEquals(enTelegram, hiTelegram)
        assertFalse(enTelegram.contains("@MR_TK_68"))
        assertFalse(bnTelegram.contains("@MR_TK_68"))
        assertFalse(arTelegram.contains("@MR_TK_68"))
        assertFalse(hiTelegram.contains("@MR_TK_68"))

        val keysToCheck = listOf(
            R.string.menu_settings,
            R.string.sec_language,
            R.string.perm_notif_title,
            R.string.profile_control_center,
            R.string.home_search_hint,
            R.string.studio_tab_qr,
            R.string.gallery_title,
            R.string.vault_title,
            R.string.player_playlist_empty,
            R.string.player_delete_playlist,
            R.string.player_all_folders_back,
            R.string.vp_subtitles_off,
            R.string.api_status_ready,
            R.string.api_test_connection
        )

        for (lang in listOf(AppLanguage.BENGALI, AppLanguage.ARABIC, AppLanguage.HINDI)) {
            for (resId in keysToCheck) {
                val enVal = LocaleHelper.getLocalizedString(context, AppLanguage.ENGLISH, resId)
                val locVal = LocaleHelper.getLocalizedString(context, lang, resId)
                assertTrue("Expected non-blank translation for $resId in ${lang.tag}", locVal.isNotBlank())
                assertNotEquals("Expected localized text for $resId in ${lang.tag}", enVal, locVal)
            }
        }

        // Verify formatted string args across all 4 locales
        for (lang in AppLanguage.entries) {
            val formatted = LocaleHelper.getLocalizedString(
                context,
                lang,
                R.string.legal_9_body,
                DeveloperIdentity.DEVELOPER_NAME,
                DeveloperIdentity.TELEGRAM_HANDLE,
                DeveloperIdentity.DEVELOPER_EMAIL
            )
            assertTrue(formatted.contains(DeveloperIdentity.DEVELOPER_EMAIL))
            assertTrue(formatted.contains(DeveloperIdentity.TELEGRAM_HANDLE))

            val officialUpdates = LocaleHelper.getLocalizedString(
                context,
                lang,
                R.string.about_official_updates,
                DeveloperIdentity.DEVELOPER_EMAIL
            )
            assertTrue(officialUpdates.contains(DeveloperIdentity.DEVELOPER_EMAIL))
            assertTrue(officialUpdates.contains("VD Player") || lang == AppLanguage.BENGALI)
        }
    }

    @Test
    fun `arabic locale sets RTL layout direction while others use LTR`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val arConfig = LocaleHelper.buildLocalizedConfiguration(context.resources.configuration, AppLanguage.ARABIC)
        val bnConfig = LocaleHelper.buildLocalizedConfiguration(context.resources.configuration, AppLanguage.BENGALI)
        val enConfig = LocaleHelper.buildLocalizedConfiguration(context.resources.configuration, AppLanguage.ENGLISH)

        assertEquals(View.LAYOUT_DIRECTION_RTL, arConfig.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, bnConfig.layoutDirection)
        assertEquals(View.LAYOUT_DIRECTION_LTR, enConfig.layoutDirection)
    }

    @Test
    fun `wrapContext translates resources and findActivity unwraps FragmentActivity`() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).create().get()
        val wrapped = LocaleHelper.wrapContext(activity, AppLanguage.BENGALI)

        assertEquals(activity, wrapped.findActivity())
        val localizedSettings = wrapped.getString(R.string.menu_settings)
        val enSettings = LocaleHelper.getLocalizedString(activity, AppLanguage.ENGLISH, R.string.menu_settings)
        assertNotEquals(enSettings, localizedSettings)
    }

    @Test
    fun `manifest declares required permissions and omits broad image or video storage permissions`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val requested = pkgInfo.requestedPermissions?.toSet().orEmpty()

        assertTrue(requested.contains(Manifest.permission.INTERNET))
        assertTrue(requested.contains(Manifest.permission.ACCESS_NETWORK_STATE))
        assertTrue(requested.contains(Manifest.permission.POST_NOTIFICATIONS))
        assertTrue(requested.contains(Manifest.permission.CAMERA))
        assertTrue(requested.contains(Manifest.permission.READ_MEDIA_AUDIO))
        assertTrue(requested.contains(Manifest.permission.USE_BIOMETRIC))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC))
        assertTrue(requested.contains(Manifest.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK))

        // Play policy compliance: zero-permission Android Photo Picker instead of broad media image/video reads
        assertFalse(requested.contains("android.permission.READ_MEDIA_IMAGES"))
        assertFalse(requested.contains("android.permission.READ_MEDIA_VIDEO"))
    }

    @Test
    fun `developer identity matches required Telegram URL email and drawable resource`() {
        assertEquals("https://t.me/MR_TK_68", DeveloperIdentity.TELEGRAM_URL)
        assertEquals("@MR_TK_68", DeveloperIdentity.TELEGRAM_HANDLE)
        assertEquals("tkofficial68@gmail.com", DeveloperIdentity.DEVELOPER_EMAIL)
        assertNotEquals(0, DeveloperIdentity.DEVELOPER_PHOTO_RES)

        val context = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(context.resources.getDrawable(DeveloperIdentity.DEVELOPER_PHOTO_RES, null))
    }
}
