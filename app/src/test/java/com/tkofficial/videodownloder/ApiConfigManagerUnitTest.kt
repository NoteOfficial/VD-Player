package com.tkofficial.videodownloder

import com.tkofficial.videodownloder.data.AppSettings
import com.tkofficial.videodownloder.network.ApiConfigManager
import com.tkofficial.videodownloder.network.ApiConfigStatus
import com.tkofficial.videodownloder.network.MediaServiceFileConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiConfigManagerUnitTest {

    @Test
    fun `parseConfigJson parses valid app_settings payload and clamps bounds`() {
        val raw = """
            {
              "media_service": {
                "enabled": true,
                "status": "configured",
                "primary_endpoint": "https://api.example.com/v1/extract",
                "secondary_endpoint": "https://sec.example.com/v1/extract",
                "backup_endpoint": "https://bak.example.com/v1/extract",
                "api_version": "v2",
                "request_timeout_seconds": 500,
                "connect_timeout_seconds": 1,
                "retry_limit": 9
              }
            }
        """.trimIndent()

        val parsed = ApiConfigManager.parseConfigJson(raw).getOrThrow()
        assertTrue(parsed.enabled)
        assertEquals("https://api.example.com/v1/extract", parsed.primaryEndpoint)
        assertEquals("https://sec.example.com/v1/extract", parsed.secondaryEndpoint)
        assertEquals("https://bak.example.com/v1/extract", parsed.backupEndpoint)
        assertEquals("v2", parsed.apiVersion)
        assertEquals(120, parsed.requestTimeoutSeconds)
        assertEquals(5, parsed.connectTimeoutSeconds)
        assertEquals(5, parsed.retryLimit)
    }

    @Test
    fun `serializeConfigJson round-trips cleanly`() {
        val cfg = MediaServiceFileConfig(
            enabled = true,
            status = "configured",
            primaryEndpoint = "https://primary.example.org/api",
            secondaryEndpoint = "https://secondary.example.org/api",
            backupEndpoint = "",
            apiVersion = "v1",
            requestTimeoutSeconds = 45,
            connectTimeoutSeconds = 20,
            retryLimit = 3
        )
        val json = ApiConfigManager.serializeConfigJson(cfg)
        val restored = ApiConfigManager.parseConfigJson(json).getOrThrow()
        assertEquals(cfg, restored)
    }

    @Test
    fun `endpoint validation enforces https unless allowHttp is enabled`() {
        assertTrue(ApiConfigManager.isValidEndpointUrl("https://api.example.com/extract", allowHttp = false))
        assertFalse(ApiConfigManager.isValidEndpointUrl("http://api.example.com/extract", allowHttp = false))
        assertTrue(ApiConfigManager.isValidEndpointUrl("http://api.example.com/extract", allowHttp = true))
        assertFalse(ApiConfigManager.isValidEndpointUrl("ftp://api.example.com/extract", allowHttp = true))
        assertFalse(ApiConfigManager.isValidEndpointUrl("not-a-url", allowHttp = true))
        assertFalse(ApiConfigManager.isValidEndpointUrl("", allowHttp = true))
    }

    @Test
    fun `resolveOrderedEndpoints preserves priority and deduplicates`() {
        val settings = AppSettings(
            customExtractorEndpoint = "  https://p.example.com/api  ",
            secondaryExtractorEndpoint = "https://s.example.com/api",
            backupExtractorEndpoint = "https://p.example.com/api"
        )
        val endpoints = ApiConfigManager.resolveOrderedEndpoints(settings)
        assertEquals(
            listOf("https://p.example.com/api", "https://s.example.com/api"),
            endpoints
        )
    }

    @Test
    fun `computeStaticStatus handles unconfigured, invalid, and ready states`() {
        assertEquals(ApiConfigStatus.NOT_CONFIGURED, ApiConfigManager.computeStaticStatus(AppSettings()))
        assertEquals(
            ApiConfigStatus.INVALID_CONFIG,
            ApiConfigManager.computeStaticStatus(
                AppSettings(
                    apiEnabled = true,
                    customExtractorEndpoint = "http://insecure.example.com",
                    allowCleartextHttp = false
                )
            )
        )
        assertEquals(
            ApiConfigStatus.NOT_CONFIGURED,
            ApiConfigManager.computeStaticStatus(
                AppSettings(
                    apiEnabled = false,
                    customExtractorEndpoint = "https://api.example.com/extract"
                )
            )
        )
        assertEquals(
            ApiConfigStatus.CONFIGURED_READY,
            ApiConfigManager.computeStaticStatus(
                AppSettings(
                    apiEnabled = true,
                    customExtractorEndpoint = "https://api.example.com/extract"
                )
            )
        )
    }

    @Test
    fun `resolveDefaultConfig prefers enabled asset config and falls back cleanly`() {
        val enabledAsset = MediaServiceFileConfig(
            enabled = true,
            status = "configured",
            primaryEndpoint = "https://asset.example.com/resolve"
        )
        assertEquals(enabledAsset, ApiConfigManager.resolveDefaultConfig(enabledAsset))

        val disabledAsset = MediaServiceFileConfig(enabled = false, primaryEndpoint = "")
        val resolved = ApiConfigManager.resolveDefaultConfig(disabledAsset)
        assertEquals(
            com.tkofficial.videodownloder.network.CustomApiConfig.isConfigured(),
            resolved.enabled && resolved.primaryEndpoint.isNotBlank()
        )
    }

    @Test
    fun `CustomApiConfig toFileConfig clamps timeout and retry bounds cleanly`() {
        val fileCfg = com.tkofficial.videodownloder.network.CustomApiConfig.toFileConfig()
        assertTrue(fileCfg.requestTimeoutSeconds in 5..120)
        assertTrue(fileCfg.connectTimeoutSeconds in 5..60)
        assertTrue(fileCfg.retryLimit in 0..5)
        assertTrue(fileCfg.apiVersion.isNotBlank())
    }
}
