package com.stickerferry.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * 绑定的本地持久化。
 *
 * 用 SharedPreferences 存一段 JSON，而不是上 Room：
 * 数据量极小（通常 1~5 条），加一个数据库层只会让构建更慢、包更大，
 * 换不来任何实际收益。
 *
 * 存的全是本机路径，**没有任何联网行为**。表情是私密数据，
 * 这一点从存储层就要能看出来。
 */
object BindingStore {

    private const val PREFS = "sf_bindings"
    private const val KEY_LIST = "bindings_v1"

    private val json = Json {
        ignoreUnknownKeys = true   // 将来加字段时，旧数据能直接读
        encodeDefaults = true
        prettyPrint = false
    }

    private val serializer = ListSerializer(Binding.serializer())

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(ctx: Context): List<Binding> {
        val raw = prefs(ctx).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            json.decodeFromString(serializer, raw)
        } catch (e: Exception) {
            // 数据损坏（比如升级时字段变了）不应该让 App 起不来，
            // 丢掉重建即可 —— 反正绑定信息靠一次扫描就能恢复
            emptyList()
        }
    }

    fun save(ctx: Context, list: List<Binding>) {
        try {
            val raw = json.encodeToString(serializer, list)
            prefs(ctx).edit().putString(KEY_LIST, raw).apply()
        } catch (e: Exception) {
            // 写失败就保持旧数据，不抛 —— 用户不该因为存盘失败看到崩溃
        }
    }

    /** 有则更新、无则插入。返回最新的完整列表 */
    fun upsert(ctx: Context, b: Binding): List<Binding> {
        val cur = load(ctx).toMutableList()
        val i = cur.indexOfFirst { it.id == b.id }
        if (i >= 0) cur[i] = b else cur.add(b)
        save(ctx, cur)
        return cur
    }

    fun remove(ctx: Context, id: String): List<Binding> {
        val next = load(ctx).filterNot { it.id == id }
        save(ctx, next)
        return next
    }

    /** 用户改名。标记 renamed，之后扫描就不会用自动别名覆盖掉 */
    fun rename(ctx: Context, id: String, alias: String): List<Binding> {
        val next = load(ctx).map {
            if (it.id == id) it.copy(alias = alias.trim().ifBlank { it.alias }, renamed = true) else it
        }
        save(ctx, next)
        return next
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_LIST).apply()
    }
}
