package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tkofficial.videodownloder.core.DeveloperIdentity
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `app identity and localized app name match VD Player`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VD Player", appName)
        assertEquals("VD Player", DeveloperIdentity.APP_NAME)
        assertEquals("com.tkofficial.videodownloder", DeveloperIdentity.PACKAGE_NAME)
        assertEquals("Mr TK", DeveloperIdentity.DEVELOPER_NAME)
        assertEquals("@MR_TK_68", DeveloperIdentity.TELEGRAM_HANDLE)
        assertEquals("https://t.me/MR_TK_68", DeveloperIdentity.TELEGRAM_URL)
        assertEquals("tkofficial68@gmail.com", DeveloperIdentity.DEVELOPER_EMAIL)
        assertEquals("1.0.0", DeveloperIdentity.VERSION_NAME)
    }
}
