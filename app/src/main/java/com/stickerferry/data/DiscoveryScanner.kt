package com.stickerferry.data

import android.content.Context
import com.stickerferry.util.StorageAccess
import java.io.File

/**
 * 表情目录发现器。
 *
 * ================== 为什么要重写 ==================
 *
 * 上一版是「硬编码一串路径 → 逐个 canRead → 按 .png/.gif 后缀收文件」。
 * 实测在真机上扫出 0 个，原因是三处设计缺陷：
 *
 * 1. **路径靠猜**。QQ / 微信各版本、各渠道包的存储结构不一样，
 *    硬编码的候选一旦没对上，一条都命中不了，而且没有任何线索。
 *
 * 2. **只认扩展名**。微信 emoji 目录里的表情大量是纯 md5 文件名、没有后缀，
 *    按后缀过滤会全部丢掉。详见 [ImageSniffer] 的说明。
 *
 * 3. **失败不可归因**。只知道「没找到」，不知道是没装、路径不对、
 *    被 ROM 拦住，还是目录在但里面没图。用户和我都没法往下查。
 *
 * ================== 现在的做法 ==================
 *
 * 分两阶段，先快后全：
 *
 * - **阶段一 · 精确路径**：按已知的结构直接取，命中就结束，速度最快。
 * - **阶段二 · 模糊搜索**：阶段一没命中时启动。不猜路径了，
 *   而是在 QQ/微信 的数据根目录下做**有界遍历**，找出名字里带
 *   emoji / expression / favorite 等关键词的目录。换版本也能自适应。
 *
 * 全程记录 [Trace]，每一层是「路径不存在」「存在但读不了」「可读」「没匹配到」
 * 都写清楚。这既是给用户看的失败原因，也是诊断页的数据来源。
 *
 * 遍历有严格预算（目录数上限 + 深度上限 + 命中上限），
 * 防止在异常目录结构上卡死。
 */
object DiscoveryScanner {

    /* ==================== 数据结构 ==================== */

    /** 每一步的结论。ok 只表示「这一步没失败」，不代表最终有结果 */
    enum class Stage(val label: String) {
        OK("可读"),
        PKG_MISSING("未安装"),
        DIR_MISSING("目录不存在"),
        DIR_DENIED("存在但读不了"),
        NO_MATCH("无表情子目录"),
        NO_IMAGE("目录里没有图片"),
    }

    /** 一条路径的探测记录，诊断页直接铺开显示 */
    data class Trace(
        val platform: AccountScanner.Platform,
        val path: String,
        val stage: Stage,
        val childCount: Int,
        val note: String,
        /** 精确路径 / 模糊搜索 / 手动授权，用来区分这条是谁探的 */
        val via: String,
    )

    /** 一处可用的表情来源 */
    data class DirHit(
        val platform: AccountScanner.Platform,
        val key: String,
        val label: String,
        val dirs: List<File>,
        val imageCount: Int,
        val via: String,
    )

    data class ScanResult(
        val apps: Map<AccountScanner.Platform, PackageProbe.AppInfo>,
        val hits: List<DirHit>,
        val traces: List<Trace>,
        val elapsedMs: Long,
        val visitedDirs: Int,
        /** 是否已获得全文件访问权限 */
        val hasAllFiles: Boolean,
    ) {
        fun hitsOf(p: AccountScanner.Platform): List<DirHit> = hits.filter { it.platform == p }

        fun imageCountOf(p: AccountScanner.Platform): Int =
            hitsOf(p).sumOf { it.imageCount }

        val imageTotal: Int get() = hits.sumOf { it.imageCount }
    }

    /* ==================== 对外入口 ==================== */

    /**
     * 全量扫描。
     *
     * @param manualDirs 用户手动授权过的目录（SAF 或直接路径），会作为额外来源并入
     */
    fun scan(
        ctx: Context,
        manualDirs: Map<AccountScanner.Platform, List<File>> = emptyMap(),
    ): ScanResult {
        val t0 = System.currentTimeMillis()
        val apps = PackageProbe.probeAll(ctx)
        val root = StorageAccess.storageRoot()
        val traces = mutableListOf<Trace>()
        val budget = Budget(4000)
        val hits = mutableListOf<DirHit>()

        hits += scanWeChat(root, apps[AccountScanner.Platform.WECHAT], traces, budget)
        hits += scanQQ(root, apps[AccountScanner.Platform.QQ], traces, budget)
        hits += scanManual(manualDirs, traces, budget)

        // 同一目录可能被「精确路径」和「模糊搜索」同时命中，按路径去重，
        // 保留图片数更多的那个（通常是覆盖更全的那一次）
        val dedup = LinkedHashMap<String, DirHit>()
        for (h in hits) {
            val k = h.dirs.joinToString("|") { it.absolutePath }
            val old = dedup[k]
            if (old == null || h.imageCount > old.imageCount) dedup[k] = h
        }

        return ScanResult(
            apps = apps,
            hits = dedup.values.sortedWith(
                compareByDescending<DirHit> { it.imageCount }
                    .thenBy { it.platform.name }
            ),
            traces = traces,
            elapsedMs = System.currentTimeMillis() - t0,
            visitedDirs = 4000 - budget.left,
            hasAllFiles = StorageAccess.hasAllFilesAccess(),
        )
    }

    /**
     * 把一个失败的来源翻译成用户能看懂的一句话。
     * 返回 null 表示这个来源是好的。
     *
     * 这里刻意按「最可能的病因」排序，而不是按探测顺序 ——
     * 用户看到的第一句话应该是最有用的那句。
     */
    fun primaryIssue(r: ScanResult, p: AccountScanner.Platform): String? {
        if (r.imageCountOf(p) > 0) return null

        val app = r.apps[p]
        if (app == null || !app.installed) {
            return "${p.label} 没有安装在这台手机上，自然读不到表情"
        }

        val tr = r.traces.filter { it.platform == p }

        if (tr.any { it.stage == Stage.DIR_DENIED }) {
            return "目录存在但没有读取权限 —— 部分厂商 ROM 会在「所有文件访问」之上再拦一层。" +
                "去「授权与绑定」试目录授权；如果系统选择器也进不去 Android/data，" +
                "就用文件管理器把表情目录复制到手机存储根目录再扫一次"
        }
        if (tr.any { it.stage == Stage.NO_IMAGE }) {
            return "找到了表情目录，但里面没有能识别的图片文件"
        }
        if (tr.any { it.stage == Stage.NO_MATCH }) {
            return "账号目录里没有找到表情子目录，可能这个账号没收藏过表情"
        }
        if (tr.any { it.stage == Stage.OK }) {
            return "目录能读，但没有匹配到像表情目录的路径 —— 跑一次诊断看具体卡在哪"
        }
        return "所有候选路径都不存在。${p.label} 的存储结构可能和预期不同，请跑一次诊断"
    }

    /* ==================== 微信 ==================== */

    private val WECHAT_ROOTS = listOf(
        "Android/data/com.tencent.mm/MicroMsg",
        "Android/data/com.tencent.mm/Tencent/MicroMsg",
        "Android/media/com.tencent.mm/MicroMsg",
        "tencent/MicroMsg",
        "Tencent/MicroMsg",
    )

    private fun scanWeChat(
        root: File,
        app: PackageProbe.AppInfo?,
        traces: MutableList<Trace>,
        budget: Budget,
    ): List<DirHit> {
        val P = AccountScanner.Platform.WECHAT
        if (app == null || !app.installed) {
            traces += Trace(P, "—", Stage.PKG_MISSING, 0, "系统里没有 com.tencent.mm", "精确路径")
            return emptyList()
        }

        val out = mutableListOf<DirHit>()
        val seenAccount = mutableSetOf<String>()

        // ---- 阶段一：按 MicroMsg/<md5>/emoji 的标准结构找 ----
        for (rel in WECHAT_ROOTS) {
            val base = File(root, rel)
            val st = probeDir(base)
            traces += Trace(P, rel, st.stage, st.childCount, "${app.summary}；${st.note}", "精确路径")
            if (st.stage != Stage.OK) continue

            val subs = listSafe(base) ?: continue
            for (acc in subs) {
                if (!acc.isDirectory) continue
                if (!isMd5Like(acc.name)) continue
                if (!seenAccount.add(acc.name)) continue

                val emojiDirs = findEmojiDirs(acc, maxDepth = 2, budget = budget)
                if (emojiDirs.isEmpty()) {
                    traces += Trace(
                        P, "$rel/${acc.name}", Stage.NO_MATCH, 0,
                        "账号目录里没有名字像表情的子目录", "精确路径",
                    )
                    continue
                }

                val files = emojiDirs.flatMap { collectImages(it, 3) }
                if (files.isEmpty()) {
                    traces += Trace(
                        P, "$rel/${acc.name}", Stage.NO_IMAGE, 0,
                        "有 ${emojiDirs.size} 个候选子目录但没认出图片", "精确路径",
                    )
                    continue
                }

                out += DirHit(
                    platform = P,
                    key = "wx:${acc.name}",
                    label = "微信账号 ${acc.name.take(4).uppercase()}",
                    dirs = emojiDirs,
                    imageCount = files.size,
                    via = "精确路径",
                )
            }
        }

        // ---- 阶段二：结构对不上时，直接在整棵数据树里搜「长得像表情的目录」 ----
        if (out.isEmpty()) {
            val pkgDir = File(root, "Android/data/com.tencent.mm")
            val st = probeDir(pkgDir)
            traces += Trace(
                P, "Android/data/com.tencent.mm", st.stage, st.childCount,
                "模糊搜索起点；${st.note}", "模糊搜索",
            )

            if (st.stage == Stage.OK) {
                val found = findEmojiDirs(pkgDir, maxDepth = 6, budget = budget)
                if (found.isEmpty()) {
                    traces += Trace(
                        P, "Android/data/com.tencent.mm", Stage.NO_MATCH, 0,
                        "整棵树里没有名字像表情的目录", "模糊搜索",
                    )
                }
                for (d in found) {
                    val files = collectImages(d, 3)
                    if (files.isEmpty()) {
                        traces += Trace(P, d.absolutePath, Stage.NO_IMAGE, 0, "候选目录里没认出图片", "模糊搜索")
                        continue
                    }
                    val acc = nearestHexSegment(d)
                    out += DirHit(
                        platform = P,
                        key = "wx:${acc ?: d.absolutePath}",
                        label = if (acc != null) "微信账号 ${acc.take(4).uppercase()}" else "微信 · ${d.name}",
                        dirs = listOf(d),
                        imageCount = files.size,
                        via = "模糊搜索",
                    )
                }
            }
        }

        return out
    }

    /* ==================== QQ ==================== */

    /** 已知结构的精确候选：相对路径 to 说明 */
    private val QQ_EXACT = listOf(
        "Android/data/com.tencent.mobileqq/Tencent/QQ_Favorite" to "QQ 收藏表情",
        "Android/data/com.tencent.mobileqq/Tencent/QQfile_recv/CustomFace" to "QQ 聊天收到的表情",
        "Android/data/com.tencent.mobileqq/files/expression" to "QQ 表情缓存",
        "Tencent/QQ_Favorite" to "QQ 收藏表情（旧版路径）",
        "tencent/QQ_Favorite" to "QQ 收藏表情（旧版路径·小写）",
    )

    /** 模糊搜索的起点。只搜这些根，不去动全盘 */
    private val QQ_FUZZY_ROOTS = listOf(
        "Android/data/com.tencent.mobileqq",
        "Tencent",
        "tencent",
    )

    private fun scanQQ(
        root: File,
        app: PackageProbe.AppInfo?,
        traces: MutableList<Trace>,
        budget: Budget,
    ): List<DirHit> {
        val P = AccountScanner.Platform.QQ
        if (app == null || !app.installed) {
            traces += Trace(P, "—", Stage.PKG_MISSING, 0, "系统里没有 com.tencent.mobileqq", "精确路径")
            return emptyList()
        }

        val out = mutableListOf<DirHit>()

        // ---- 阶段一：精确路径 ----
        for ((rel, label) in QQ_EXACT) {
            val dir = File(root, rel)
            val st = probeDir(dir)
            traces += Trace(P, rel, st.stage, st.childCount, "$label；${st.note}", "精确路径")
            if (st.stage != Stage.OK) continue

            val files = collectImages(dir, 4)
            if (files.isEmpty()) {
                traces += Trace(P, rel, Stage.NO_IMAGE, st.childCount, "$label：目录里没认出图片", "精确路径")
                continue
            }
            out += DirHit(
                platform = P,
                key = "qq:${dir.absolutePath}",
                label = label,
                dirs = listOf(dir),
                imageCount = files.size,
                via = "精确路径",
            )
        }

        // ---- 阶段二：模糊搜索 ----
        if (out.isEmpty()) {
            for (rel in QQ_FUZZY_ROOTS) {
                val base = File(root, rel)
                val st = probeDir(base)
                traces += Trace(P, rel, st.stage, st.childCount, "模糊搜索起点；${st.note}", "模糊搜索")
                if (st.stage != Stage.OK) continue

                val found = findEmojiDirs(base, maxDepth = 6, budget = budget)
                if (found.isEmpty()) {
                    traces += Trace(P, rel, Stage.NO_MATCH, 0, "这里没有名字像表情的目录", "模糊搜索")
                }
                for (d in found) {
                    val files = collectImages(d, 3)
                    if (files.isEmpty()) {
                        traces += Trace(P, d.absolutePath, Stage.NO_IMAGE, 0, "候选目录里没认出图片", "模糊搜索")
                        continue
                    }
                    out += DirHit(
                        platform = P,
                        key = "qq:${d.absolutePath}",
                        label = "QQ · ${d.name}",
                        dirs = listOf(d),
                        imageCount = files.size,
                        via = "模糊搜索",
                    )
                }
            }
        }

        return out
    }

    /* ==================== 手动授权目录 ==================== */

    private fun scanManual(
        manual: Map<AccountScanner.Platform, List<File>>,
        traces: MutableList<Trace>,
        budget: Budget,
    ): List<DirHit> {
        val out = mutableListOf<DirHit>()
        for ((p, dirs) in manual) {
            for (d in dirs) {
                val st = probeDir(d)
                traces += Trace(p, d.absolutePath, st.stage, st.childCount, "手动授权；${st.note}", "手动授权")
                if (st.stage != Stage.OK) continue
                // 手动指定的目录：既收本层，也收子目录里的图片
                val files = collectImages(d, 5)
                if (files.isEmpty()) {
                    traces += Trace(p, d.absolutePath, Stage.NO_IMAGE, st.childCount, "手动目录里没认出图片", "手动授权")
                    continue
                }
                out += DirHit(
                    platform = p,
                    key = "manual:${d.absolutePath}",
                    label = "手动指定 · ${d.name}",
                    dirs = listOf(d),
                    imageCount = files.size,
                    via = "手动授权",
                )
            }
        }
        return out
    }

    /* ==================== 文件遍历 ==================== */

    /** 目录数预算，防止异常结构把扫描拖死 */
    private class Budget(var left: Int) {
        fun spend(): Boolean {
            if (left <= 0) return false
            left--
            return true
        }
    }

    private data class DirState(val stage: Stage, val childCount: Int, val note: String)

    /**
     * 探测一个目录的真实状态。
     *
     * 关键点：`exists()` 为真但 `listFiles()` 返回 null，说明目录在、权限没有。
     * 这两种情况必须分开 —— 前者是「QQ 没装」，后者是「系统拦了」，
     * 处理方式完全不同。
     */
    private fun probeDir(dir: File): DirState {
        val exists = try { dir.exists() } catch (e: Exception) { false }
        if (!exists) return DirState(Stage.DIR_MISSING, 0, "路径不存在")

        val isDir = try { dir.isDirectory } catch (e: Exception) { false }
        if (!isDir) return DirState(Stage.DIR_MISSING, 0, "同名文件存在，但不是目录")

        val children = try { dir.listFiles() } catch (e: Exception) { null }
            ?: return DirState(Stage.DIR_DENIED, 0, "目录在，但列不出内容（被权限拦截）")

        return DirState(Stage.OK, children.size, "可读，${children.size} 个子项")
    }

    private fun listSafe(dir: File): Array<File>? =
        try { dir.listFiles() } catch (e: Exception) { null }

    /** 目录名里带这些词，就认为它可能是表情目录 */
    private val EMOJI_WORDS = listOf(
        "emoji", "emoticon", "expression", "favorite", "customface", "sticker", "emotion",
    )

    private fun isEmojiDirName(name: String): Boolean {
        val n = name.lowercase()
        return EMOJI_WORDS.any { n.contains(it) }
    }

    private fun isMd5Like(s: String): Boolean =
        s.length == 32 && s.all { it in '0'..'9' || it in 'a'..'f' }

    /** 往上找最近的 32 位十六进制目录名，用来把表情目录归到某个微信账号下 */
    private fun nearestHexSegment(f: File): String? {
        var cur: File? = f
        var guard = 0
        while (cur != null && guard < 12) {
            if (isMd5Like(cur.name)) return cur.name
            cur = cur.parentFile
            guard++
        }
        return null
    }

    /**
     * 有界遍历，找出所有「名字像表情」的目录。
     *
     * 两个刻意的取舍：
     * - 命中后**不再往下递归**。否则 `emoji/emoji/emoji_jpg` 这种嵌套
     *   会被重复计数好几遍。
     * - 命中数封顶 40 个。正常手机不可能超过，超过说明匹配规则太宽了。
     */
    private fun findEmojiDirs(start: File, maxDepth: Int, budget: Budget): List<File> {
        val out = mutableListOf<File>()

        fun walk(d: File, depth: Int) {
            if (depth > maxDepth || out.size >= 40) return
            if (!budget.spend()) return

            val children = listSafe(d) ?: return
            for (c in children) {
                if (!c.isDirectory) continue
                if (isEmojiDirName(c.name)) {
                    out += c
                    continue
                }
                walk(c, depth + 1)
            }
        }

        walk(start, 0)
        return out
    }

    /* ---- 图片识别 ---- */

    /** 明确是图片的扩展名：直接收，不必读文件头 */
    private val FAST_IMAGE_EXT = setOf("png", "gif", "jpg", "jpeg", "webp", "bmp")

    /** 明确不是图片的扩展名：直接跳过，省掉一次读文件头 */
    private val SKIP_EXT = setOf(
        "db", "db-wal", "db-shm", "sqlite", "xml", "json", "txt", "so",
        "ini", "log", "tmp", "lock", "cfg", "conf", "html", "htm",
        "js", "css", "proto", "idx", "zip", "apk", "ttf", "otf",
    )

    private fun looksLikeImage(f: File): Boolean {
        val name = f.name
        val dot = name.lastIndexOf('.')
        if (dot in 1 until name.length - 1) {
            val ext = name.substring(dot + 1).lowercase()
            if (ext in FAST_IMAGE_EXT) return true
            if (ext in SKIP_EXT) return false
        }
        // 无扩展名或未知扩展名 —— 只能看文件头。微信的表情大多走这条分支
        return ImageSniffer.sniff(f) != ImageSniffer.Kind.UNKNOWN
    }

    /**
     * 递归收集图片。深度与数量都有上限，任何一层读失败都静默跳过 ——
     * 一个坏目录不该让整个扫描崩掉。
     */
    fun collectImages(dir: File, maxDepth: Int = 4, cap: Int = 4000): List<File> {
        val out = mutableListOf<File>()

        fun walk(d: File, depth: Int) {
            if (depth > maxDepth || out.size >= cap) return
            val children = listSafe(d) ?: return
            for (f in children) {
                if (out.size >= cap) return
                if (f.isDirectory) {
                    walk(f, depth + 1)
                } else if (looksLikeImage(f)) {
                    out.add(f)
                }
            }
        }

        walk(dir, 0)
        return out
    }
}
