package com.stickerferry.util

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * API Key 的加密存储。
 *
 * 为什么不用普通 SharedPreferences：明文存储可以被 root 设备或备份工具读走。
 * 为什么不用 BuildConfig：写死在代码里，反编译就泄露，会被盗刷产生真金白银的损失。
 *
 * EncryptedSharedPreferences 用 Android Keystore 里的硬件级密钥加密，
 * App 卸载即销毁，是自用工具的标准做法。
 */
object SecureStore {

    private const val FILE = "sf_secure"
    private const val KEY_API = "ai_api_key"
    private const val KEY_PROVIDER = "ai_provider"

    private var cached: SharedPreferences? = null

    private fun prefs(ctx: Context): SharedPreferences {
        cached?.let { return it }
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        val p = EncryptedSharedPreferences.create(
            ctx,
            FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        cached = p
        return p
    }

    /** 服务商类型 */
    enum class Provider { DOMESTIC, OVERSEAS }

    fun saveKey(ctx: Context, key: String) {
        prefs(ctx).edit().putString(KEY_API, key.trim()).apply()
    }

    fun getKey(ctx: Context): String? = prefs(ctx).getString(KEY_API, null)

    fun saveProvider(ctx: Context, provider: Provider) {
        prefs(ctx).edit().putString(KEY_PROVIDER, provider.name).apply()
    }

    fun getProvider(ctx: Context): Provider {
        val name = prefs(ctx).getString(KEY_PROVIDER, Provider.DOMESTIC.name)
        return runCatching { Provider.valueOf(name!!) }.getOrDefault(Provider.DOMESTIC)
    }

    /** 是否已配置 —— 生成按钮靠这个判断要不要弹引导 */
    fun isConfigured(ctx: Context): Boolean = !getKey(ctx).isNullOrBlank()

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
    }

    /**
     * 脱敏展示：只显示头尾几位，中间打码。
     * 界面上回显时用这个，避免完整 Key 曝露在截屏里。
     */
    fun masked(key: String?): String {
        if (key.isNullOrBlank()) return ""
        return if (key.length <= 10) "••••••"
        else "${key.take(6)}••••••${key.takeLast(4)}"
    }
}
