package com.stickerferry.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * 「所有文件访问权限」（MANAGE_EXTERNAL_STORAGE）的检查与申请。
 *
 * 为什么需要它：
 * Android 11+ 默认禁止第三方 App 访问 Android/data/ 目录，
 * 而 QQ 和微信的表情恰好都放在那里。没有这个权限，用户只能手动
 * 用系统选择器一个个点目录，既慢又容易选错。
 *
 * 有了它，App 可以直接遍历文件系统，自动找到所有账号目录 ——
 * 这才是「自动读取」真正的前提。
 *
 * ⚠️ 边界说明：这个权限属于高敏感权限，上架 Google Play 会被拒。
 * 但本工具是自用、不上架，所以可以用。这一点必须让用户知情。
 */
object StorageAccess {

    /**
     * 是否已获得「所有文件访问权限」。
     *
     * Android 10 及以下没有这个概念，只要有 READ_EXTERNAL_STORAGE 就能读
     * 大部分目录，所以老系统直接返回 true。
     */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Environment.isExternalStorageManager()
            } catch (e: Exception) {
                false
            }
        } else {
            true
        }

    /**
     * 跳转到系统的「所有文件访问权限」开关页。
     *
     * 厂商 ROM 上这个页面的入口不统一，所以做了降级链：
     * 应用专属页面 → 全局页面 → 应用详情页。
     */
    fun openAllFilesSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return

        val intents = listOf(
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${ctx.packageName}")
            },
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
            },
        )

        for (intent in intents) {
            try {
                ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (e: Exception) {
                // 换下一个入口
            }
        }
    }

    /** 外部存储根目录，通常是 /storage/emulated/0 */
    fun storageRoot(): File = Environment.getExternalStorageDirectory()

    /**
     * 检查一个目录是否真的可读。
     *
     * 有权限不代表目录存在，目录存在也不代表能读（部分 ROM 会二次拦截），
     * 所以这里用「能不能列出来」做最终判定。
     */
    fun canRead(dir: File): Boolean {
        if (!dir.exists() || !dir.isDirectory) return false
        return try {
            dir.list() != null
        } catch (e: Exception) {
            false
        }
    }
}
