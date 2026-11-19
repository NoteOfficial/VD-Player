package com.tkofficial.videodownloder.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class ExtractorApiRequest(
    @Json(name = "url") val url: String
)

@JsonClass(generateAdapter = true)
data class ExtractorOptionDto(
    @Json(name = "label") val label: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "mimeType") val mimeType: String? = null,
    @Json(name = "quality") val quality: String? = null,
    @Json(name = "sizeBytes") val sizeBytes: Long? = null
)

@JsonClass(generateAdapter = true)
data class ExtractorApiResponse(
    @Json(name = "status") val status: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "thumbnail") val thumbnail: String? = null,
    @Json(name = "durationMs") val durationMs: Long? = null,
    @Json(name = "filename") val filename: String? = null,
    @Json(name = "options") val options: List<ExtractorOptionDto>? = null,
    @Json(name = "error") val error: String? = null
)

interface CustomExtractorService {
    @POST
    suspend fun resolveMedia(
        @Url endpointUrl: String,
        @Header("Authorization") authorization: String?,
        @Header("Accept") accept: String = "application/json",
        @Body request: ExtractorApiRequest
    ): Response<ExtractorApiResponse>
}
