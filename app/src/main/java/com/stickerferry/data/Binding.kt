package com.stickerferry.data

import com.stickerferry.util.StorageAccess
import kotlinx.serialization.Serializable
import java.io.File

/**
 * 一条「账号绑定」。
 *
 * 为什么要单独做这层，而不是每次扫描完就丢掉：
 *
 * 1. **用户要能命名**。拿不到真实 QQ 号 / 微信号，扫描结果只能是
 *    「微信账号 A3F2」这种机器标签。绑定后用户可以改成「我的大号」「工作号」，
 *    这是他唯一能建立认知的手段。
 * 2. **要能记住授权**。目录一旦授权就存下来，下次启动不用重新点。
 * 3. **要能报健康状态**。微信一重装、目录一改名，绑定就该显示「目录已消失」，
 *    而不是默默变成 0 让用户以为软件坏了。
 *
 * 存的是路径字符串而不是 File —— 序列化后必须能跨重启、跨版本还原。
 */
@Serializable
data class Binding(
    /** 稳定 id。用「平台:发现器的 key」，同一个目录重复扫描不会产生重复绑定 */
    val id: String,
    /** "QQ" / "WECHAT"，存字符串而非 enum，避免以后加平台时旧数据反序列化失败 */
    val platform: String,
    /** 用户可改的显示名 */
    val alias: String,
    /** 绑定的目录绝对路径 */
    val paths: List<String>,
    /** 这个绑定是怎么来的：精确路径 / 模糊搜索 / 手动授权 */
    val via: String,
    /**
     * 目录的访问方式：
     * - "FILE" 直接文件路径，依赖「所有文件访问权限」
     * - "SAF"  系统目录授权（content:// URI），权限由系统持久化
     *
     * 两种都要支持，因为 SAF 在 Android 11+ 上选不了 Android/data，
     * 但在老系统、以及 /sdcard/Tencent 这类目录上仍然是最可靠的兜底。
     */
    val sourceKind: String = "FILE",
    val createdAt: Long,
    val lastScanAt: Long = 0L,
    /** 上次扫描时的图片数，用于「目录没了」这类状态判断 */
    val lastCount: Int = 0,
    /** 用户是否手动命名过。自动生成的别名可以被扫描结果覆盖，手动的不行 */
    val renamed: Boolean = false,
) {

    val platformEnum: AccountScanner.Platform
        get() = if (platform == AccountScanner.Platform.QQ.name) {
            AccountScanner.Platform.QQ
        } else {
            AccountScanner.Platform.WECHAT
        }

    val files: List<File> get() = paths.map { File(it) }

    /** 展示用的短路径。只留最后两段，免得一行放不下 */
    val shortPath: String
        get() {
            val p = paths.firstOrNull() ?: return "—"
            if (p.startsWith("content://")) {
                // SAF 给的是编码过的 content:// URI，解出来才能给人看
                val decoded = try { android.net.Uri.decode(p) } catch (e: Exception) { p }
                val tail = decoded.substringAfterLast(':', "").trim('/')
                return if (tail.isBlank()) "系统目录授权" else "…/" + tail.takeLast(28)
            }
            val parts = p.split('/').filter { it.isNotBlank() }
            return if (parts.size <= 2) p else "…/" + parts.takeLast(2).joinToString("/")
        }
}

/**
 * 绑定的实时健康状态。
 *
 * 刻意跟 Binding 分开：Binding 是持久化的事实（我当时绑了哪个目录），
 * Health 是这一刻的检查结果（那个目录现在还活着吗）。混在一起会导致
 * 每次读数据都要做一次磁盘 IO。
 */
enum class BindingStatus(val label: String) {
    ACTIVE("正常"),
    EMPTY("暂无表情"),

    /** 目录整个不见了：微信重装、换手机、用户手动删了 */
    PATH_MISSING("目录已消失"),

    /** 目录还在但读不了：权限被回收，或被 ROM 拦截 */
    DENIED("无读取权限"),

    /** 绑了但从没扫描成功过 */
    PENDING("待扫描"),
}

data class BindingHealth(
    val binding: Binding,
    val status: BindingStatus,
    val detail: String,
    val count: Int,
) {
    val healthy: Boolean
        get() = status == BindingStatus.ACTIVE || status == BindingStatus.EMPTY
}

object BindingHealthChecker {

    /**
     * 检查一条绑定的状态。
     *
     * 注意：这里**不数文件**。数文件很贵（几千个文件的目录要几秒），
     * 而状态判断只需要知道「能不能读」。数量用上次扫描的结果。
     */
    fun check(ctx: android.content.Context, b: Binding): BindingHealth = when (b.sourceKind) {
        "SAF" -> checkSaf(ctx, b)
        else -> checkFile(b)
    }

    private fun checkSaf(ctx: android.content.Context, b: Binding): BindingHealth {
        val alive = b.paths.any { p ->
            val uri = try { android.net.Uri.parse(p) } catch (e: Exception) { null }
            uri != null && com.stickerferry.util.SafHelper.isUriValid(ctx, uri)
        }
        return when {
            alive && b.lastCount > 0 ->
                BindingHealth(b, BindingStatus.ACTIVE, "目录授权有效 · ${b.lastCount} 个表情", b.lastCount)
            alive ->
                BindingHealth(b, BindingStatus.EMPTY, "目录授权有效，但里面没有图片", 0)
            b.lastScanAt == 0L ->
                BindingHealth(b, BindingStatus.PENDING, "还没扫描过", 0)
            else ->
                BindingHealth(b, BindingStatus.PATH_MISSING, "目录授权已失效，需要重新选一次目录", 0)
        }
    }

    private fun checkFile(b: Binding): BindingHealth {
        if (b.paths.isEmpty()) {
            return BindingHealth(b, BindingStatus.PATH_MISSING, "没有绑定任何目录", 0)
        }

        if (!StorageAccess.hasAllFilesAccess()) {
            return BindingHealth(
                b, BindingStatus.DENIED,
                "缺少「所有文件访问权限」，到授权与绑定里开启", b.lastCount,
            )
        }

        var anyExists = false
        var anyReadable = false

        for (f in b.files) {
            val exists = try { f.exists() } catch (e: Exception) { false }
            if (exists) anyExists = true
            if (StorageAccess.canRead(f)) {
                anyReadable = true
                break
            }
        }

        return when {
            anyReadable -> if (b.lastCount > 0) {
                BindingHealth(b, BindingStatus.ACTIVE, "可读 · ${b.lastCount} 个表情", b.lastCount)
            } else {
                BindingHealth(b, BindingStatus.EMPTY, "可读，但里面没有图片", 0)
            }

            anyExists -> BindingHealth(
                b, BindingStatus.DENIED,
                "目录存在但读不了，可能被系统或厂商 ROM 拦截", b.lastCount,
            )

            b.lastScanAt == 0L -> BindingHealth(b, BindingStatus.PENDING, "还没扫描过", 0)

            else -> BindingHealth(
                b, BindingStatus.PATH_MISSING,
                "绑定的目录已经不在了（重装或清理过？）", 0,
            )
        }
    }

    fun checkAll(ctx: android.content.Context, list: List<Binding>): Map<String, BindingHealth> =
        list.associate { it.id to check(ctx, it) }
}
