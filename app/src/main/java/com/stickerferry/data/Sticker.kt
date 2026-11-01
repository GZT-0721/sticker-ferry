package com.stickerferry.data

import android.net.Uri

/**
 * 表情来源。
 * QQ / WECHAT —— 从聊天软件本地目录扫到的
 * ALBUM        —— 用户从相册选的
 * AI_GEN       —— AI 生成的
 * STOCK        —— 从图库检索来的素材
 */
enum class Source { QQ, WECHAT, ALBUM, AI_GEN, STOCK }

/**
 * 统一的表模型。不管图从哪来，进了 App 都长这个样子。
 */
data class Sticker(
    val id: String,
    val uri: Uri,
    val name: String,
    val source: Source,
    val isAnim: Boolean,
    val sizeBytes: Long,
    val addedAt: Long,
    /** 惰性计算的感知哈希，用于去重 */
    var hash: String? = null,
) {
    /** 展示用的来源名 */
    val sourceLabel: String
        get() = when (source) {
            Source.QQ -> "QQ"
            Source.WECHAT -> "微信"
            Source.ALBUM -> "相册"
            Source.AI_GEN -> "AI 生成"
            Source.STOCK -> "图库"
        }

    /** 是否可以直接写入 QQ 目录 */
    val writable: Boolean
        get() = true
}

/**
 * 扫描结果。区分成功与失败，让界面能显示"部分成功"状态。
 */
data class ScanReport(
    val qq: List<Sticker> = emptyList(),
    val wechat: List<Sticker> = emptyList(),
    val qqError: String? = null,
    val wechatError: String? = null,
) {
    val all: List<Sticker> get() = qq + wechat
    val total: Int get() = qq.size + wechat.size

    /** 是否有来源读取失败 */
    val hasPartialFailure: Boolean
        get() = qqError != null || wechatError != null
}
