package com.stickerferry.data

import android.net.Uri
import com.stickerferry.util.StorageAccess
import java.io.File

/**
 * 账号与表情目录的自动发现。
 *
 * ⚠️ 先把边界说清楚：
 *
 * 1. **拿不到明文账号**。腾讯不对外暴露 QQ 号 / 微信号，这也是系统层面的限制
 *    （AccountManager 里腾讯没有注册 account type）。拿不到的我们绝不假装拿到。
 *
 * 2. **能识别「有几个账号」**。微信把每个登录过的账号的数据放在
 *    MicroMsg/<md5(uin)>/ 目录下，目录名是 32 位十六进制。据此可以分辨账号，
 *    并给出一个稳定的区分标签（如「微信账号 A3F2」）。
 *
 * 3. **QQ 侧不做账号区分**。QQ 的收藏表情放在 Tencent/QQ_Favorite，
 *    是全局共享目录，不按账号隔离。所以按「表情类型」组织，而不是按账号。
 *
 * 有了「所有文件访问权限」，这些目录可以直接用 File API 遍历，
 * 不再需要用户手动一个个点系统选择器。
 */
object AccountScanner {

    enum class Platform(val label: String) {
        QQ("QQ"),
        WECHAT("微信"),
    }

    /**
     * 一个「账号」或一类表情来源。
     * 微信侧一个 Account = 一个登录过的账号；QQ 侧一个 Account = 一类表情目录。
     */
    data class Account(
        val key: String,
        val platform: Platform,
        val label: String,
        val subtitle: String,
        val dirs: List<File>,
        val stickerCount: Int,
    )

    private val IMG_EXT = setOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".bmp")

    /* ==================== 对外入口 ==================== */

    /** 扫描全部账号，返回可用的表情来源列表 */
    fun scanAll(): List<Account> {
        val root = StorageAccess.storageRoot()
        return scanWeChat(root) + scanQQ(root)
    }

    /** 把某个账号下的所有表情转成统一的 Sticker 模型 */
    fun loadStickers(account: Account): List<Sticker> {
        val out = mutableListOf<Sticker>()
        for (dir in account.dirs) {
            for (f in listImages(dir, 4)) {
                val lower = f.name.lowercase()
                out.add(
                    Sticker(
                        id = "${account.key}:${f.absolutePath}",
                        uri = Uri.fromFile(f),
                        name = f.name,
                        source = if (account.platform == Platform.QQ) Source.QQ else Source.WECHAT,
                        isAnim = lower.endsWith(".gif") || lower.endsWith(".webp"),
                        sizeBytes = f.length(),
                        addedAt = f.lastModified(),
                    )
                )
            }
        }
        return out
    }

    /** 诊断信息：告诉界面「哪些路径找到了、哪些没找到」 */
    data class Diagnosis(
        val hasPermission: Boolean,
        val found: List<String>,
        val missing: List<String>,
    )

    fun diagnose(): Diagnosis {
        val root = StorageAccess.storageRoot()
        val found = mutableListOf<String>()
        val missing = mutableListOf<String>()

        for ((name, dir) in allCandidateDirs(root)) {
            if (StorageAccess.canRead(dir)) found.add(name) else missing.add(name)
        }
        return Diagnosis(StorageAccess.hasAllFilesAccess(), found, missing)
    }

    /* ==================== 微信 ==================== */

    private fun scanWeChat(root: File): List<Account> {
        val out = mutableListOf<Account>()
        val bases = listOf(
            File(root, "Android/data/com.tencent.mm/MicroMsg"),
            File(root, "tencent/MicroMsg"),
            File(root, "Tencent/MicroMsg"),
        )

        for (base in bases) {
            if (!StorageAccess.canRead(base)) continue
            val subs = try { base.listFiles() } catch (e: Exception) { null } ?: continue

            for (dir in subs) {
                if (!dir.isDirectory) continue
                // 微信账号目录名 = md5(uin)，固定 32 位小写十六进制
                if (!isMd5Like(dir.name)) continue

                val emojiDirs = listOf(
                    File(dir, "emoji"),
                    File(dir, "emoji/emoji"),
                    File(dir, "emoji/emoji_jpg"),
                ).filter { StorageAccess.canRead(it) }

                if (emojiDirs.isEmpty()) continue

                val count = emojiDirs.sumOf { listImages(it, 4).size }
                if (count == 0) continue

                val shortId = dir.name.take(4).uppercase()
                out.add(
                    Account(
                        key = "wx:${dir.name}",
                        platform = Platform.WECHAT,
                        label = "微信账号 $shortId",
                        subtitle = "目录 ${dir.parentFile?.name ?: "MicroMsg"}/${dir.name.take(8)}…",
                        dirs = emojiDirs,
                        stickerCount = count,
                    )
                )
            }
        }
        return out
    }

    private fun isMd5Like(s: String): Boolean =
        s.length == 32 && s.all { it in '0'..'9' || it in 'a'..'f' }

    /* ==================== QQ ==================== */

    private fun scanQQ(root: File): List<Account> {
        val specs = listOf(
            Triple(
                File(root, "Android/data/com.tencent.mobileqq/Tencent/QQ_Favorite"),
                "QQ 收藏表情",
                "我 → 设置 → 表情 → 我的表情",
            ),
            Triple(
                File(root, "Android/data/com.tencent.mobileqq/Tencent/QQfile_recv/CustomFace"),
                "QQ 收到表情",
                "聊天中收到的自定义表情",
            ),
            Triple(
                File(root, "Android/data/com.tencent.mobileqq/files/expression"),
                "QQ 表情缓存",
                "客户端表情缓存目录",
            ),
            Triple(
                File(root, "Tencent/QQ_Favorite"),
                "QQ 收藏（旧版路径）",
                "旧版本 QQ 的收藏目录",
            ),
        )

        val out = mutableListOf<Account>()
        for ((dir, label, desc) in specs) {
            if (!StorageAccess.canRead(dir)) continue
            val files = listImages(dir, 4)
            if (files.isEmpty()) continue

            out.add(
                Account(
                    key = "qq:${dir.absolutePath}",
                    platform = Platform.QQ,
                    label = label,
                    subtitle = desc,
                    dirs = listOf(dir),
                    stickerCount = files.size,
                )
            )
        }
        return out
    }

    /* ==================== 工具 ==================== */

    /** 所有候选路径，仅用于诊断展示 */
    private fun allCandidateDirs(root: File): List<Pair<String, File>> = listOf(
        "微信 MicroMsg" to File(root, "Android/data/com.tencent.mm/MicroMsg"),
        "QQ 收藏表情" to File(root, "Android/data/com.tencent.mobileqq/Tencent/QQ_Favorite"),
        "QQ 收到表情" to File(root, "Android/data/com.tencent.mobileqq/Tencent/QQfile_recv/CustomFace"),
        "QQ 表情缓存" to File(root, "Android/data/com.tencent.mobileqq/files/expression"),
        "QQ 收藏（旧版）" to File(root, "Tencent/QQ_Favorite"),
    )

    /**
     * 递归列出图片。深度上限防死循环（微信目录偶尔会有软链）。
     * 任何一层读取失败都静默跳过 —— 一个坏目录不该让整个扫描崩掉。
     */
    fun listImages(dir: File, maxDepth: Int): List<File> {
        val out = mutableListOf<File>()

        fun walk(d: File, depth: Int) {
            if (depth > maxDepth) return
            val children = try { d.listFiles() } catch (e: Exception) { null } ?: return
            for (f in children) {
                if (f.isDirectory) {
                    walk(f, depth + 1)
                } else {
                    val n = f.name.lowercase()
                    if (IMG_EXT.any { n.endsWith(it) }) out.add(f)
                }
            }
        }

        walk(dir, 0)
        return out
    }
}
