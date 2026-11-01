package com.stickerferry.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * 图库检索客户端 —— 双模式设计。
 *
 * 设计取舍（重要）：
 * 自用场景下，用户有权自己决定搜什么。所以这里不做技术封锁，
 * 而是把判断依据摆到明面上：结果带来源域名 + 许可标注。
 *
 * 不替用户拦，但也不假装问题不存在。
 */
class StockSearchClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /** 检索模式 */
    enum class Mode {
        /** 默认。只查白名单可商用图库，结果带许可标签 */
        SAFE,
        /** 可选。全网检索，结果标注来源域名 + 用途未知 */
        ALL,
    }

    enum class License { COMMERCIAL_OK, UNKNOWN }

    data class StockImage(
        val id: String,
        val thumbUrl: String,
        val fullUrl: String,
        val title: String,
        /** 展示给用户看：来自哪个域名 */
        val sourceDomain: String,
        val license: License,
        val author: String = "",
    )

    /**
     * 统一入口。两种模式共用返回结构，区别只在数据源与许可标注。
     */
    suspend fun search(query: String, mode: Mode): List<StockImage> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) return@withContext emptyList()

            when (mode) {
                // 只碰明确可商用的图库
                Mode.SAFE -> {
                    val merged = mutableListOf<StockImage>()
                    runCatching { searchPexels(query) }.getOrNull()?.let { merged.addAll(it) }
                    runCatching { searchPixabay(query) }.getOrNull()?.let { merged.addAll(it) }
                    merged.map { it.copy(license = License.COMMERCIAL_OK) }
                }

                // 全网检索：结果必须标注来源，且许可未知
                Mode.ALL -> {
                    runCatching { searchWeb(query) }.getOrDefault(emptyList())
                        .map { it.copy(license = License.UNKNOWN) }
                }
            }
        }

    /** Pexels 检索（需在官网申请免费 Key） */
    private fun searchPexels(query: String): List<StockImage> {
        val key = PEXELS_KEY
        if (key.isBlank()) return emptyList()

        val url = "https://api.pexels.com/v1/search?query=$query&per_page=20"
        val req = Request.Builder().url(url).addHeader("Authorization", key).build()

        http.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val json = JSONObject(resp.body?.string() ?: "")
            val photos = json.optJSONArray("photos") ?: return emptyList()

            val out = mutableListOf<StockImage>()
            for (i in 0 until photos.length()) {
                val p = photos.optJSONObject(i) ?: continue
                out.add(
                    StockImage(
                        id = "pexels_${p.optInt("id")}",
                        thumbUrl = p.optJSONObject("src")?.optString("medium") ?: continue,
                        fullUrl = p.optJSONObject("src")?.optString("original") ?: continue,
                        title = p.optString("alt", "素材 ${i + 1}"),
                        sourceDomain = "pexels.com",
                        license = License.COMMERCIAL_OK,
                        author = p.optString("photographer"),
                    )
                )
            }
            return out
        }
    }

    /** Pixabay 检索（需在官网申请免费 Key） */
    private fun searchPixabay(query: String): List<StockImage> {
        val key = PIXABAY_KEY
        if (key.isBlank()) return emptyList()

        val url = "https://pixabay.com/api/?key=$key&q=$query&per_page=20&image_type=photo"
        http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            if (!resp.isSuccessful) return emptyList()
            val json = JSONObject(resp.body?.string() ?: "")
            val hits = json.optJSONArray("hits") ?: return emptyList()

            val out = mutableListOf<StockImage>()
            for (i in 0 until hits.length()) {
                val h = hits.optJSONObject(i) ?: continue
                out.add(
                    StockImage(
                        id = "pixabay_${h.optInt("id")}",
                        thumbUrl = h.optString("previewURL"),
                        fullUrl = h.optString("largeImageURL"),
                        title = h.optString("tags", "素材 ${i + 1}"),
                        sourceDomain = "pixabay.com",
                        license = License.COMMERCIAL_OK,
                        author = h.optString("user"),
                    )
                )
            }
            return out
        }
    }

    /**
     * 全网检索。
     *
     * ⚠️ 这里刻意不做"全网爬取"的实现，只留接口位。
     * 原因：真正的全网爬取会引入反爬对抗、格式混乱、水印素材等问题，
     * 工程量大且收益小。如果要接，建议接一个合规的聚合搜索 API，
     * 而不是自己去爬各家网站。
     */
    private fun searchWeb(query: String): List<StockImage> {
        // 实际接入时替换为聚合搜索服务的实现
        return emptyList()
    }

    companion object {
        // ⚠️ 这两个 Key 是图库自己的（不是 AI 模型 Key）。
        // 留空则跳过该图库。真实使用时去各图库官网免费申请。
        private const val PEXELS_KEY = ""
        private const val PIXABAY_KEY = ""
    }
}
