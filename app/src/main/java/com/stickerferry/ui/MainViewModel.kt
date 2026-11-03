package com.stickerferry.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stickerferry.data.AccountScanner
import com.stickerferry.data.Binding
import com.stickerferry.data.BindingHealth
import com.stickerferry.data.BindingHealthChecker
import com.stickerferry.data.BindingStatus
import com.stickerferry.data.BindingStore
import com.stickerferry.data.DiscoveryScanner
import com.stickerferry.data.PackageProbe
import com.stickerferry.data.ScanReport
import com.stickerferry.data.Source
import com.stickerferry.data.Sticker
import com.stickerferry.data.StickerRepository
import com.stickerferry.util.SecureStore
import com.stickerferry.util.StorageAccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 表情库的筛选条件 */
enum class Filter { ALL, QQ, WECHAT, ANIM }

/**
 * 全局 ViewModel。整个 App 共享一份状态，避免各页面数据不同步。
 *
 * 数据流是这样的：
 *
 *   系统权限 / 目录授权
 *        ↓
 *   DiscoveryScanner 发现目录
 *        ↓
 *   Binding 落盘（可命名、可解绑、可看健康状态）
 *        ↓
 *   Sticker 列表 → 表情库
 *
 * 中间多一层 Binding 而不是「扫完直接出列表」，是为了：
 * 用户能命名、能记住授权、能在目录失效时看到明确原因。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val ctx = app.applicationContext
    private val repo = StickerRepository(ctx)

    // ---------------- 表情列表 ----------------
    private val _report = MutableStateFlow(ScanReport())
    val report: StateFlow<ScanReport> = _report.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    // ---------------- 发现与绑定 ----------------
    private val _discovery = MutableStateFlow<DiscoveryScanner.ScanResult?>(null)
    val discovery: StateFlow<DiscoveryScanner.ScanResult?> = _discovery.asStateFlow()

    private val _bindings = MutableStateFlow<List<Binding>>(emptyList())
    val bindings: StateFlow<List<Binding>> = _bindings.asStateFlow()

    private val _health = MutableStateFlow<Map<String, BindingHealth>>(emptyMap())
    val health: StateFlow<Map<String, BindingHealth>> = _health.asStateFlow()

    /** 从绑定表派生的展示行，表情库用 */
    private val _accounts = MutableStateFlow<List<AccountScanner.Account>>(emptyList())
    val accounts: StateFlow<List<AccountScanner.Account>> = _accounts.asStateFlow()

    // ---------------- 授权状态 ----------------
    private val _storageOk = MutableStateFlow(StorageAccess.hasAllFilesAccess())
    val storageOk: StateFlow<Boolean> = _storageOk.asStateFlow()

    private val _qqUri = MutableStateFlow<Uri?>(null)
    val qqUri: StateFlow<Uri?> = _qqUri.asStateFlow()

    private val _wechatUri = MutableStateFlow<Uri?>(null)
    val wechatUri: StateFlow<Uri?> = _wechatUri.asStateFlow()

    // ---------------- 诊断 ----------------
    private val _diagnosing = MutableStateFlow(false)
    val diagnosing: StateFlow<Boolean> = _diagnosing.asStateFlow()

    private val _diagnoseText = MutableStateFlow("")
    val diagnoseText: StateFlow<String> = _diagnoseText.asStateFlow()

    // ---------------- 投放队列 ----------------
    private val _queue = MutableStateFlow<List<Sticker>>(emptyList())
    val queue: StateFlow<List<Sticker>> = _queue.asStateFlow()

    // ---------------- AI 配置 ----------------
    private val _aiConfigured = MutableStateFlow(SecureStore.isConfigured(ctx))
    val aiConfigured: StateFlow<Boolean> = _aiConfigured.asStateFlow()

    init {
        // 先把上次的绑定捞出来，界面立刻有内容，不用等扫描
        val saved = BindingStore.load(ctx)
        _bindings.value = saved
        if (saved.isNotEmpty()) {
            _health.value = BindingHealthChecker.checkAll(ctx, saved)
            rebuildAccounts()
        }
        // 有权限就直接开扫 —— 这是「打开就有」的关键
        if (StorageAccess.hasAllFilesAccess()) autoScan()
    }

    /* ==================== 筛选 / 选择 ==================== */

    fun visible(): List<Sticker> {
        val all = _report.value.all
        return when (_filter.value) {
            Filter.ALL -> all
            Filter.QQ -> all.filter { it.source == Source.QQ }
            Filter.WECHAT -> all.filter { it.source == Source.WECHAT }
            Filter.ANIM -> all.filter { it.isAnim }
        }
    }

    fun setFilter(f: Filter) { _filter.value = f }

    fun toggleSelect(id: String) {
        val cur = _selected.value.toMutableSet()
        if (!cur.add(id)) cur.remove(id)
        _selected.value = cur
    }

    fun selectAll() { _selected.value = visible().map { it.id }.toSet() }

    fun clearSelection() { _selected.value = emptySet() }

    /* ==================== 扫描 ==================== */

    /**
     * 全量刷新：发现 → 合并绑定 → 载入表情 → 更新健康状态。
     *
     * 每一步的失败都往上带原因，不让界面出现「0 个表情」这种无信息量的结果。
     */
    fun autoScan() {
        viewModelScope.launch {
            _scanning.value = true
            try {
                val ok = StorageAccess.hasAllFilesAccess()
                _storageOk.value = ok

                // 1) 发现目录
                val result = withContext(Dispatchers.IO) {
                    DiscoveryScanner.scan(ctx, emptyMap())
                }
                _discovery.value = result

                // 2) 并入绑定表（自动绑定；用户改过名的保留用户的命名）
                val merged = mergeDiscovery(result)
                _bindings.value = merged
                withContext(Dispatchers.IO) { BindingStore.save(ctx, merged) }

                // 3) 载入表情：文件路径绑定 + SAF 授权绑定
                val fromFiles = withContext(Dispatchers.IO) { loadFileBindings(merged) }
                val saf = withContext(Dispatchers.IO) { repo.scanAll(_qqUri.value, _wechatUri.value) }
                syncSafBindingCount(saf.qq.size, saf.wechat.size)

                val qq = (fromFiles.filter { it.source == Source.QQ } + saf.qq).distinctBy { it.id }
                val wx = (fromFiles.filter { it.source == Source.WECHAT } + saf.wechat).distinctBy { it.id }

                // 4) 健康状态
                val hs = withContext(Dispatchers.IO) { BindingHealthChecker.checkAll(ctx, merged) }
                _health.value = hs
                rebuildAccounts()

                // 5) 组装结果 + 失败原因
                val qqIssue = if (qq.isEmpty()) {
                    DiscoveryScanner.primaryIssue(result, AccountScanner.Platform.QQ)
                        ?: saf.qqError
                        ?: "没读到 QQ 表情"
                } else null

                val wxIssue = if (wx.isEmpty()) {
                    DiscoveryScanner.primaryIssue(result, AccountScanner.Platform.WECHAT)
                        ?: saf.wechatError
                        ?: "没读到微信表情"
                } else null

                _report.value = ScanReport(
                    qq = qq.sortedByDescending { it.addedAt },
                    wechat = wx.sortedByDescending { it.addedAt },
                    qqError = qqIssue,
                    wechatError = wxIssue,
                )
            } catch (e: Exception) {
                _report.value = ScanReport(
                    qqError = "扫描出错了：${e.message ?: e.javaClass.simpleName}",
                    wechatError = "请跑一次诊断看具体卡在哪一步",
                )
            } finally {
                _scanning.value = false
            }
        }
    }

    /** SAF 模式下的手动扫描，保留给「手动授权目录」用 */
    fun scan() = autoScan()

    /** 把发现结果写进绑定表 */
    private fun mergeDiscovery(result: DiscoveryScanner.ScanResult): List<Binding> {
        val cur = BindingStore.load(ctx).toMutableList()
        val now = System.currentTimeMillis()

        for (hit in result.hits) {
            val id = "${hit.platform.name}:${hit.key}"
            val paths = hit.dirs.map { it.absolutePath }
            val i = cur.indexOfFirst { it.id == id }

            if (i >= 0) {
                val old = cur[i]
                if (old.sourceKind == "SAF") continue
                cur[i] = old.copy(
                    alias = if (old.renamed) old.alias else hit.label,
                    paths = paths,
                    via = hit.via,
                    lastScanAt = now,
                    lastCount = hit.imageCount,
                )
            } else {
                cur += Binding(
                    id = id,
                    platform = hit.platform.name,
                    alias = hit.label,
                    paths = paths,
                    via = hit.via,
                    sourceKind = "FILE",
                    createdAt = now,
                    lastScanAt = now,
                    lastCount = hit.imageCount,
                )
            }
        }
        return cur
    }

    private fun loadFileBindings(list: List<Binding>): List<Sticker> {
        val out = mutableListOf<Sticker>()
        for (b in list) {
            if (b.sourceKind != "FILE") continue
            out += AccountScanner.loadStickers(b.platformEnum, b.files, b.id)
        }
        return out
    }

    /** SAF 绑定不经过 DiscoveryScanner，数量要单独回填，否则永远显示 0 */
    private fun syncSafBindingCount(qqCount: Int, wxCount: Int) {
        val cur = _bindings.value
        if (cur.none { it.sourceKind == "SAF" }) return
        val now = System.currentTimeMillis()
        val next = cur.map { b ->
            if (b.sourceKind != "SAF") b
            else when (b.platformEnum) {
                AccountScanner.Platform.QQ -> b.copy(lastScanAt = now, lastCount = qqCount)
                AccountScanner.Platform.WECHAT -> b.copy(lastScanAt = now, lastCount = wxCount)
            }
        }
        _bindings.value = next
        viewModelScope.launch(Dispatchers.IO) { BindingStore.save(ctx, next) }
    }

    private fun rebuildAccounts() {
        val hs = _health.value
        _accounts.value = _bindings.value.map { b ->
            val h = hs[b.id]
            AccountScanner.Account(
                key = b.id,
                platform = b.platformEnum,
                label = b.alias,
                subtitle = "${h?.status?.label ?: "待扫描"} · ${b.shortPath}",
                dirs = b.files,
                stickerCount = h?.count ?: b.lastCount,
            )
        }
    }

    /* ==================== 授权与绑定 ==================== */

    /**
     * 目录授权：用户通过系统选择器指定的目录。
     *
     * 这是「自动扫描失败」之后的第二道兜底。它的价值有两块：
     * - 老系统（Android 10 及以下）上能选中 Android/data 里的子目录
     * - 用户把表情保存到了自建目录时，可以手动指过去
     */
    fun addSafBinding(platform: AccountScanner.Platform, uri: Uri) {
        val now = System.currentTimeMillis()
        val alias = when (platform) {
            AccountScanner.Platform.QQ -> "QQ 手动授权目录"
            AccountScanner.Platform.WECHAT -> "微信手动授权目录"
        }
        val b = Binding(
            id = "saf:${platform.name}:$uri",
            platform = platform.name,
            alias = alias,
            paths = listOf(uri.toString()),
            via = "手动授权",
            sourceKind = "SAF",
            createdAt = now,
            lastScanAt = now,
            lastCount = 0,
        )
        BindingStore.upsert(ctx, b)

        when (platform) {
            AccountScanner.Platform.QQ -> _qqUri.value = uri
            AccountScanner.Platform.WECHAT -> _wechatUri.value = uri
        }
        autoScan()
    }

    fun onQqPicked(uri: Uri) = addSafBinding(AccountScanner.Platform.QQ, uri)

    fun onWechatPicked(uri: Uri) = addSafBinding(AccountScanner.Platform.WECHAT, uri)

    fun renameBinding(id: String, alias: String) {
        _bindings.value = BindingStore.rename(ctx, id, alias)
        _health.value = BindingHealthChecker.checkAll(ctx, _bindings.value)
        rebuildAccounts()
    }

    fun unbind(id: String) {
        _bindings.value = BindingStore.remove(ctx, id)
        _health.value = BindingHealthChecker.checkAll(ctx, _bindings.value)
        rebuildAccounts()
        autoScan()
    }

    /** 从系统设置返回后：刷新权限，如果刚拿到就立刻重扫 */
    fun refreshStoragePermission() {
        val now = StorageAccess.hasAllFilesAccess()
        val was = _storageOk.value
        _storageOk.value = now
        if (now && !was) autoScan()
    }

    /** 检查某条绑定是否需要修复（给界面判定用） */
    fun needsRepair(id: String): Boolean {
        val h = _health.value[id] ?: return false
        return h.status == BindingStatus.PATH_MISSING || h.status == BindingStatus.DENIED
    }

    /* ==================== 诊断 ==================== */

    /**
     * 生成一份纯文本诊断报告。
     *
     * 设计目标：用户不需要理解任何技术细节，直接把这段发给我，
     * 我就能知道卡在哪一层。所以每一行都是「状态 + 路径 + 说明」。
     */
    fun runDiagnose() {
        viewModelScope.launch {
            _diagnosing.value = true
            try {
                val result = withContext(Dispatchers.IO) {
                    DiscoveryScanner.scan(ctx, emptyMap())
                }
                _discovery.value = result
                _diagnoseText.value = buildDiagnoseText(result)
            } catch (e: Exception) {
                _diagnoseText.value = "诊断失败：${e.message ?: e.javaClass.simpleName}"
            } finally {
                _diagnosing.value = false
            }
        }
    }

    private fun buildDiagnoseText(r: DiscoveryScanner.ScanResult): String {
        val sb = StringBuilder()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)

        sb.appendLine("表情摆渡 · 诊断报告")
        sb.appendLine("时间：${fmt.format(Date())}")
        sb.appendLine("系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        sb.appendLine("机型：${Build.MANUFACTURER} ${Build.MODEL}")
        sb.appendLine("厂商 ROM：${Build.DISPLAY}")
        sb.appendLine()

        sb.appendLine("【权限】")
        sb.appendLine("所有文件访问权限：${if (r.hasAllFiles) "已授予" else "未授予"}")
        sb.appendLine("存储根目录：${StorageAccess.storageRoot().absolutePath}")
        sb.appendLine()

        sb.appendLine("【应用】")
        for (p in AccountScanner.Platform.entries) {
            val pkg = if (p == AccountScanner.Platform.QQ) PackageProbe.PKG_QQ else PackageProbe.PKG_WECHAT
            val app = r.apps[p]
            sb.appendLine("${p.label}（$pkg）：${app?.summary ?: "未知"}")
        }
        sb.appendLine()

        sb.appendLine("【扫描概况】")
        sb.appendLine("命中来源：${r.hits.size} 处")
        sb.appendLine("识别到图片：${r.imageTotal} 张")
        sb.appendLine("遍历目录数：${r.visitedDirs}")
        sb.appendLine("耗时：${r.elapsedMs} ms")
        sb.appendLine()

        sb.appendLine("【命中明细】")
        if (r.hits.isEmpty()) {
            sb.appendLine("（无）")
        } else {
            for (h in r.hits) {
                sb.appendLine("${h.platform.label} · ${h.label} · ${h.imageCount} 张 · 来源=${h.via}")
                for (d in h.dirs) sb.appendLine("    ${d.absolutePath}")
            }
        }
        sb.appendLine()

        for (p in AccountScanner.Platform.entries) {
            sb.appendLine("【${p.label} 探测路径】")
            val traces = r.traces.filter { it.platform == p }
            if (traces.isEmpty()) {
                sb.appendLine("（无记录）")
            } else {
                for (t in traces) {
                    sb.appendLine("[${t.stage.label}] ${t.via} | 子项 ${t.childCount} | ${t.path}")
                    sb.appendLine("    ${t.note}")
                }
            }
            sb.appendLine("结论：${DiscoveryScanner.primaryIssue(r, p) ?: "正常"}")
            sb.appendLine()
        }

        sb.appendLine("【已绑定】")
        val bs = _bindings.value
        if (bs.isEmpty()) {
            sb.appendLine("（无）")
        } else {
            for (b in bs) {
                val h = _health.value[b.id]
                sb.appendLine("${b.alias} · ${b.platform} · ${h?.status?.label ?: "待扫描"} · ${h?.count ?: 0} 张")
                sb.appendLine("    ${b.paths.joinToString(" , ")}")
            }
        }

        return sb.toString()
    }

    /* ==================== 投放 / AI ==================== */

    fun addSelectionToQueue() {
        _queue.value = _report.value.all.filter { it.id in _selected.value }
        _selected.value = emptySet()
    }

    fun removeFromQueue(id: String) {
        _queue.value = _queue.value.filterNot { it.id == id }
    }

    fun clearQueue() { _queue.value = emptyList() }

    fun refreshAiConfig() {
        _aiConfigured.value = SecureStore.isConfigured(ctx)
    }
}
