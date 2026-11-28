package com.tokimusic.app.data.sync
import com.tokimusic.app.data.AppStrings
import com.tokimusic.app.R

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 条 18：Drive REST v3 的最小手写客户端，只操作 appDataFolder 里的
 * tokimusic-sync.json 一个文件（list → download / create / update）。
 * 令牌由调用方传入；任何失败抛异常，由同步层静默/提示处理。
 */
class DriveSyncClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
) {
    private fun authed(builder: Request.Builder, token: String): Request.Builder =
        builder.header("Authorization", "Bearer $token")

    suspend fun findFileId(token: String): String? = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder" +
            "&q=name%3D%27${SyncBundleCodec.FILE_NAME}%27" +
            "&fields=files(id,name)"
        val request = authed(Request.Builder().url(url).get(), token).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { AppStrings.getOrFallback(R.string.err_drive_list, "Drive 列表失败 HTTP %1\$d", response.code) }
            val body = response.body?.string() ?: return@withContext null
            val files = JSONObject(body).optJSONArray("files") ?: return@withContext null
            if (files.length() == 0) null else files.getJSONObject(0).optString("id").ifBlank { null }
        }
    }

    suspend fun download(token: String, fileId: String): String? = withContext(Dispatchers.IO) {
        val request = authed(
            Request.Builder().url("https://www.googleapis.com/drive/v3/files/$fileId?alt=media").get(),
            token,
        ).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { AppStrings.getOrFallback(R.string.err_drive_download, "Drive 下载失败 HTTP %1\$d", response.code) }
            response.body?.string()
        }
    }

    /** fileId 为空 = 新建（multipart），否则媒体更新。返回文件 id。 */
    suspend fun upload(token: String, fileId: String?, content: String): String = withContext(Dispatchers.IO) {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        if (fileId == null) {
            val metadata = JSONObject()
                .put("name", SyncBundleCodec.FILE_NAME)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("metadata", null, metadata.toString().toRequestBody("application/json".toMediaType()))
                .addFormDataPart("file", SyncBundleCodec.FILE_NAME, content.toRequestBody(mediaType))
                .build()
            val request = authed(
                Request.Builder().url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart").post(body),
                token,
            ).build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { AppStrings.getOrFallback(R.string.err_drive_upload, "Drive 上传失败 HTTP %1\$d", response.code) }
                JSONObject(response.body?.string() ?: "{}").optString("id")
            }
        } else {
            val request = authed(
                Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$fileId?uploadType=media")
                    .method("PATCH", content.toRequestBody(mediaType)),
                token,
            ).build()
            http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { AppStrings.getOrFallback(R.string.err_drive_update, "Drive 更新失败 HTTP %1\$d", response.code) }
            }
            fileId
        }
    }
}
