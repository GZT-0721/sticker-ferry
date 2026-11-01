package com.stickerferry.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.stickerferry.data.ScanReport
import com.stickerferry.data.Source
import com.stickerferry.data.Sticker
import com.stickerferry.data.StickerRepository
import com.stickerferry.util.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

    /** 重新扫描两个来源 */
    fun scan() {
        viewModelScope.launch {
            _scanning.value = true
            val r = repo.scanAll(_qqUri.value, _wechatUri.value)
            _report.value = r
            _scanning.value = false
        }
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
