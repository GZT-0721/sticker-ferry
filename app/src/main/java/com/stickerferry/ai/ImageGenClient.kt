package com.stickerferry.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.stickerferry.util.SecureStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 文生图接口客户端。
 *
 * ⚠️ 前提：用户必须自己配置 API Key 并按量付费。
 * 原因很简单 —— 生图跑在云端，手机算不动，必须有调用通道。
 * 这个成本只能由使用者承担。
 *
 * 注意：不同服务商的接口路径与参数格式不同，这里给的是通用骨架，
 * 实际接入时改 PRC_ENDPOINT 和 buildRequestBody 即可。
 */
class ImageGenClient(private val ctx: Context) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)   // 生图较慢，读超时要放宽
        .build()

    /** 各家服务商的生图端点，实际使用时替换 */
    private fun endpoint(): String = when (SecureStore.getProvider(ctx)) {
        SecureStore.Provider.DOMESTIC -> "https://api.example-cn.com/v1/images/generations"
        SecureStore.Provider.OVERSEAS -> "https://api.example.com/v1/images/generations"
    }

    sealed class Result {
        data class Ok(val bitmaps: List<Bitmap>) : Result()
        data class Fail(val message: String) : Result()
    }

    /**
     * 生成表情图片。
     *
     * @param userInput 用户写的描述
     * @param style     风格（来自 PromptBuilder.STYLES）
     * @param count     生成几张（通常 4 张给用户挑）
     */
    suspend fun generate(
        userInput: String,
        style: String,
        count: Int = 4,
    ): Result = withContext(Dispatchers.IO) {

        // 1. 先检查 Key
        val key = SecureStore.getKey(ctx)
        if (key.isNullOrBlank()) {
            return@withContext Result.Fail("NOT_CONFIGURED")
        }

        // 2. 敏感内容拦截
        PromptBuilder.checkPolicy(userInput)?.let {
            return@withContext Result.Fail(it)
        }

        // 3. 组装提示词
        val prompt = PromptBuilder.build(userInput, style)

        try {
            val body = JSONObject().apply {
                put("prompt", prompt)
                put("n", count)
                put("aspect_ratio", "1:1")       // 表情标准比例
                put("background", "transparent")  // 透明背景，省掉抠图
            }.toString().toRequestBody("application/json".toMediaType())

            val req = Request.Builder()
                .url(endpoint())
                .addHeader("Authorization", "Bearer $key")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    val code = resp.code
                    val msg = when (code) {
                        401, 403 -> "API Key 无效或已过期，请到设置里重新配置"
                        429 -> "请求太频繁，或额度已用完，稍后再试"
                        else -> "接口返回错误（$code）"
                    }
                    return@withContext Result.Fail(msg)
                }

                val json = JSONObject(resp.body?.string() ?: "")
                val urls = parseImageUrls(json)
                if (urls.isEmpty()) return@withContext Result.Fail("接口没有返回图片")

                // 4. 下载图片字节并解码
                val bitmaps = urls.mapNotNull { u ->
                    runCatching { downloadBitmap(u) }.getOrNull()
                }
                if (bitmaps.isEmpty()) Result.Fail("图片下载失败")
                else Result.Ok(bitmaps)
            }
        } catch (e: Exception) {
            // 网络异常如实告知，不吞掉
            Result.Fail("网络异常：${e.message ?: "未知错误"}")
        }
    }

    /** 解析返回的图片地址 / base64 */
    private fun parseImageUrls(json: JSONObject): List<String> {
        val out = mutableListOf<String>()
        val arr = json.optJSONArray("data") ?: json.optJSONArray("images") ?: return out
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            item.optString("url").takeIf { it.isNotBlank() }?.let { out.add(it) }
            item.optString("b64_json").takeIf { it.isNotBlank() }?.let { out.add("b64:$it") }
        }
        return out
    }

    private fun downloadBitmap(url: String): Bitmap? {
        if (url.startsWith("b64:")) {
            val bytes = android.util.Base64.decode(url.removePrefix("b64:"), android.util.Base64.DEFAULT)
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
        val req = Request.Builder().url(url).build()
        http.newCall(req).execute().use { resp ->
            val bytes = resp.body?.bytes() ?: return null
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        }
    }
}
