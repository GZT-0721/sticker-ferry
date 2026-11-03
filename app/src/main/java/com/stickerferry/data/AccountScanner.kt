package com.stickerferry.data

import android.net.Uri
import java.io.File

/**
 * 账号 / 来源的统一模型。
 *
 * 这里只剩「数据长什么样」，实际的文件查找已经搬到 [DiscoveryScanner]。
 * 分开的原因：查找逻辑会随着 QQ / 微信改版本不断变，而展示模型基本不变，
 * 混在一起改一次动全身。
 *
 * ⚠️ 边界（说过很多次，代码里也留一份）：
 * 拿不到明文 QQ 号 / 微信号。腾讯没有向系统注册 account type，
 * 目录名又是单向 md5。所以账号只能用「微信账号 A3F2」这类稳定但非明文的标签。
 */
object AccountScanner {

    enum class Platform(val label: String) {
        QQ("QQ"),
        WECHAT("微信"),
    }

    /**
     * 一个可展示的表情来源。
     * 微信侧一个 Account = 一个登录过的账号；
     * QQ 侧一个 Account = 一类表情目录（收藏 / 收到 / 缓存）。
     */
    data class Account(
        val key: String,
        val platform: Platform,
        val label: String,
        val subtitle: String,
        val dirs: List<File>,
        val stickerCount: Int,
    )

    /** 从发现结果转成展示模型 */
    fun fromHit(hit: DiscoveryScanner.DirHit, alias: String? = null): Account = Account(
        key = "${hit.platform.name}:${hit.key}",
        platform = hit.platform,
        label = alias ?: hit.label,
        subtitle = hit.dirs.firstOrNull()?.absolutePath ?: "—",
        dirs = hit.dirs,
        stickerCount = hit.imageCount,
    )

    /** 把某个来源下的所有图片转成统一的 Sticker 模型 */
    fun loadStickers(platform: Platform, dirs: List<File>, keyPrefix: String): List<Sticker> {
        val out = mutableListOf<Sticker>()
        val fallbackSource = if (platform == Platform.QQ) Source.QQ else Source.WECHAT

        for (dir in dirs) {
            for (f in DiscoveryScanner.collectImages(dir, 4)) {
                val kind = ImageSniffer.sniff(f)
                if (kind == ImageSniffer.Kind.UNKNOWN) continue
                out.add(
                    Sticker(
                        id = "$keyPrefix:${f.absolutePath}",
                        uri = Uri.fromFile(f),
                        name = f.name,
                        source = fallbackSource,
                        isAnim = ImageSniffer.isAnimated(f, kind),
                        sizeBytes = f.length(),
                        addedAt = f.lastModified(),
                    )
                )
            }
        }
        return out
    }
}
