package com.stickerferry.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stickerferry.data.AccountScanner
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

/** 表情库的筛选条件 */
enum class Filter { ALL, QQ, WECHAT, ANIM }

/**
 * 全局 ViewModel。整个 App 共享一份状态，避免各页面数据不同步。
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = StickerRepository(app.applicationContext)
    private val ctx = app.applicationContext

    // ---- 扫描与表情库 ----
    private val _report = MutableStateFlow(ScanReport())
    val report: StateFlow<ScanReport> = _report.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _filter = MutableStateFlow(Filter.ALL)
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _selected = MutableStateFlow<Set<String>>(emptySet())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    // ---- 目录授权 ----
    private val _qqUri = MutableStateFlow<Uri?>(null)
    val qqUri: StateFlow<Uri?> = _qqUri.asStateFlow()

    private val _wechatUri = MutableStateFlow<Uri?>(null)
    val wechatUri: StateFlow<Uri?> = _wechatUri.asStateFlow()

    // ---- 投放队列 ----
    private val _queue = MutableStateFlow<List<Sticker>>(emptyList())
    val queue: StateFlow<List<Sticker>> = _queue.asStateFlow()

    // ---- AI 配置 ----
    private val _aiConfigured = MutableStateFlow(SecureStore.isConfigured(ctx))
    val aiConfigured: StateFlow<Boolean> = _aiConfigured.asStateFlow()

    // ---- 自动发现的账号 ----
    private val _accounts = MutableStateFlow<List<AccountScanner.Account>>(emptyList())
    val accounts: StateFlow<List<AccountScanner.Account>> = _accounts.asStateFlow()

    /** 是否已获得「所有文件访问权限」—— 自动扫描的前提 */
    private val _storageOk = MutableStateFlow(StorageAccess.hasAllFilesAccess())
    val storageOk: StateFlow<Boolean> = _storageOk.asStateFlow()

    /** 当前筛选后要展示的表情 */
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

    fun selectAll() {
        _selected.value = visible().map { it.id }.toSet()
    }

    fun clearSelection() { _selected.value = emptySet() }

    /** 记录用户授权的目录，并立刻扫描 */
    fun onQqPicked(uri: Uri) {
        _qqUri.value = uri
        scan()
    }

    fun onWechatPicked(uri: Uri) {
        _wechatUri.value = uri
        scan()
    }

    /** 重新扫描两个来源（SAF 模式，需用户手动选目录） */
    fun scan() {
        viewModelScope.launch {
            _scanning.value = true
            val r = repo.scanAll(_qqUri.value, _wechatUri.value)
            _report.value = r
            _scanning.value = false
        }
    }

    /**
     * 自动扫描：直接遍历文件系统，找出所有账号目录和其中表情。
     *
     * 前提是已授予「所有文件访问权限」。没权限时明确告知并引导，
     * 而不是静默返回空列表让用户一脸茫然。
     */
    fun autoScan() {
        viewModelScope.launch {
            _scanning.value = true
            val ok = StorageAccess.hasAllFilesAccess()
            _storageOk.value = ok

            if (!ok) {
                _accounts.value = emptyList()
                _report.value = ScanReport(
                    qqError = "缺少「所有文件访问权限」，无法读取 Android/data 目录",
                    wechatError = "到设置里开启该权限后重试自动扫描",
                )
                _scanning.value = false
                return@launch
            }

            val accs = withContext(Dispatchers.IO) { AccountScanner.scanAll() }
            _accounts.value = accs

            val all = withContext(Dispatchers.IO) {
                accs.flatMap { AccountScanner.loadStickers(it) }
            }

            val qqList = all.filter { it.source == Source.QQ }
            val wxList = all.filter { it.source == Source.WECHAT }

            _report.value = ScanReport(
                qq = qqList,
                wechat = wxList,
                qqError = if (qqList.isEmpty()) {
                    "没找到 QQ 表情目录（QQ 未安装 / 没收藏过表情 / 系统拦截了目录访问）"
                } else null,
                wechatError = if (wxList.isEmpty()) {
                    "没找到微信表情目录（未登录过微信 / 没缓存过表情）"
                } else null,
            )
            _scanning.value = false
        }
    }

    /** 从系统设置返回后刷新权限状态 */
    fun refreshStoragePermission() {
        _storageOk.value = StorageAccess.hasAllFilesAccess()
    }

    /** 把选中的表情加入投放队列 */
    fun addSelectionToQueue() {
        val picked = _report.value.all.filter { it.id in _selected.value }
        _queue.value = picked
        _selected.value = emptySet()
    }

    fun removeFromQueue(id: String) {
        _queue.value = _queue.value.filterNot { it.id == id }
    }

    fun clearQueue() { _queue.value = emptyList() }

    /** 配置保存后刷新状态 */
    fun refreshAiConfig() {
        _aiConfigured.value = SecureStore.isConfigured(ctx)
    }
}
