package com.stickerferry.data

import android.content.Context
import android.net.Uri
import com.stickerferry.util.SafHelper

/**
 * 微信表情目录读取。
 *
 * ⚠️ 重要说明（决定了 App 能力的边界）：
 *
 * 微信 4.x 之后，表情的**元数据**存在加密的 WCDB 数据库里，
 * 需要从运行中进程的内存中取 raw key 才能解密 —— 那是逆向工程，
 * 技术脆弱且有法律风险，本项目**明确不做**。
 *
 * 我们能读的只是缓存目录里**未加密的图片文件**。
 * 能读多少读多少，读不到的不假装能读。
 *
 * 已知路径：
 *   /Android/data/com.tencent.mm/MicroMsg/<账号哈希>/emoji/
 *   /Android/data/com.tencent.mm/MicroMsg/<账号哈希>/OpenData/
 *   /sdcard/tencent/MicroMsg/<账号哈希>/emoji/     （Android 10 及以下）
 */
object WeChatSource {

    val PATH_HINTS = listOf(
        "Android/data/com.tencent.mm/MicroMsg",
        "Android/data/com.tencent.mm/MicroMsg/<账号>/emoji",
        "tencent/MicroMsg/<账号>/emoji",
    )

    /** 微信加密文件的常见特征：无扩展名，或扩展名被混淆 */
    private fun looksEncrypted(name: String): Boolean {
        val lower = name.lowercase()
        // 没有任何已知图片扩展名 → 多半是加密缓存
        val known = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp")
        return known.none { lower.endsWith(it) }
    }

    /**
     * 扫描微信目录。
     * 只收能被系统识别为图片的文件，加密文件直接跳过（不做任何破解尝试）。
     */
    fun scan(ctx: Context, treeUri: Uri): Pair<List<Sticker>, String?> {
        return try {
            // 注意：这里只列一次。上一版列了两次（files 和 allFiles 各一次），
            // 既慢一倍，算出来的「跳过几个」也是错的。
            val files = SafHelper.listImages(ctx, treeUri)

            var encrypted = 0
            val list = files.mapNotNull { f ->
                val uri = f.uri
                val name = f.name ?: return@mapNotNull null
                // SafHelper 已经按文件头识别过一遍，能走到这里的都是真图片。
                // 这里再判一次扩展名，只为统计「有多少个是不带后缀的」，
                // 这类文件通常是微信的加密缓存，用户可能需要知道。
                if (looksEncrypted(name)) encrypted++
                Sticker(
                    id = uri.toString(),
                    uri = uri,
                    name = name,
                    source = Source.WECHAT,
                    isAnim = SafHelper.isAnimated(name) ||
                        SafHelper.sniffMime(ctx, uri) == "image/gif",
                    sizeBytes = f.length(),
                    addedAt = f.lastModified(),
                )
            }

            val warn = when {
                list.isEmpty() ->
                    "微信目录里没有可直接读取的图片（缓存多为加密文件，本工具不做破解）"
                encrypted > 0 ->
                    "已读取 ${list.size} 个，其中 $encrypted 个是无后缀文件，靠文件头识别出来的"
                else -> null
            }

            list to warn
        } catch (e: Exception) {
            emptyList<Sticker>() to (e.message ?: "微信目录读取失败")
        }
    }
}
