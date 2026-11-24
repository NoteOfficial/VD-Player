package com.tkofficial.videodownloder

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tkofficial.videodownloder.core.DeveloperIdentity
import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.network.ApiConfigManager
import com.tkofficial.videodownloder.network.ApiConfigStatus
import com.tkofficial.videodownloder.network.LinkAnalyzer
import com.tkofficial.videodownloder.security.SecurityManager
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SecurityAndNetworkTest {

    @Test
    fun `PBKDF2 PIN hashing uses unique salt and validates properly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val security = SecurityManager(context)

        val (hash1, salt1) = security.hashPin("2468")
        val (hash2, salt2) = security.hashPin("2468")

        assertNotEquals(salt1, salt2)
        assertNotEquals(hash1, hash2)
        assertTrue(security.verifyPin("2468", hash1, salt1))
        assertTrue(security.verifyPin("2468", hash2, salt2))
        assertFalse(security.verifyPin("1357", hash1, salt1))
        assertFalse(security.verifyPin("", hash1, salt1))
    }

    @Test
    fun `integrity check inspects package identity cleanly`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val report = SecurityManager(context).runIntegrityCheck()

        assertEquals(DeveloperIdentity.PACKAGE_NAME, report.expectedPackageName)
        assertEquals(context.packageName == DeveloperIdentity.PACKAGE_NAME, report.packageValid)
        assertTrue(report.checkedAtMillis > 0L)
    }

    @Test
    fun `link analyzer rejects disallowed cleartext and invalid urls offline`() = runTest {
        val analyzer = LinkAnalyzer(OkHttpClient())

        val httpResult = analyzer.analyze(
            rawUrl = "http://example.com/media.mp4",
            allowHttp = false,
            timeoutSeconds = 10,
            customEndpoint = "",
            decryptedApiKey = ""
        )
        assertTrue(httpResult.isFailure)

        val badScheme = analyzer.analyze(
            rawUrl = "ftp://example.com/media.mp4",
            allowHttp = true,
            timeoutSeconds = 10,
            customEndpoint = "",
            decryptedApiKey = ""
        )
        assertTrue(badScheme.isFailure)
    }

    @Test
    fun `testConnection recognizes POST-only JSON API when HEAD returns 404`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val seenMethods = mutableListOf<String>()
        var capturedApiKeyHeader: String? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val req = chain.request()
                seenMethods += req.method
                capturedApiKeyHeader = req.header("X-API-Key")
                when (req.method) {
                    "HEAD" -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(404)
                        .message("Not Found")
                        .body("".toResponseBody("text/plain".toMediaType()))
                        .build()
                    else -> Response.Builder()
                        .request(req)
                        .protocol(Protocol.HTTP_1_1)
                        .code(400)
                        .message("Bad Request")
                        .header("Content-Type", "application/json")
                        .body("""{"error":"url required"}""".toResponseBody("application/json".toMediaType()))
                        .build()
                }
            })
            .build()

        val manager = ApiConfigManager(context, client)
        val result = manager.testConnection(
            settings = AppSettings(
                apiEnabled = true,
                customExtractorEndpoint = "https://extractor.example.com/api/extract",
                retryLimit = 2
            ),
            decryptedApiKey = "secret-token-123"
        )

        assertEquals(ApiConfigStatus.CONNECTED, result.status)
        assertEquals(listOf("HEAD", "POST"), seenMethods)
        assertEquals("secret-token-123", capturedApiKeyHeader)
    }

    @Test
    fun `testConnection returns AUTH_FAILED on 401 without redundant retries`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        var callCount = 0
        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                callCount++
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("""{"error":"Invalid key"}""".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val manager = ApiConfigManager(context, client)
        val result = manager.testConnection(
            settings = AppSettings(
                apiEnabled = true,
                customExtractorEndpoint = "https://extractor.example.com/api/extract",
                retryLimit = 3
            ),
            decryptedApiKey = "bad-key"
        )

        assertEquals(ApiConfigStatus.AUTH_FAILED, result.status)
        assertEquals(1, callCount)
    }
}
