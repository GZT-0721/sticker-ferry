package com.stickerferry.data

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * 探测 QQ / 微信 是否真的装在手机上，以及装的是哪个版本。
 *
 * 这一步看起来多余，其实是诊断里最关键的一环：
 * 当扫描结果是 0 时，必须先回答「是没装」还是「装了但路径不对」。
 * 不分清这两件事，后面的排查全是瞎猜。
 *
 * 拿到的版本号也有用 —— 不同大版本的存储结构差别很大，
 * 版本号写进诊断报告后，才能针对性地去对路径。
 */
object PackageProbe {

    const val PKG_QQ = "com.tencent.mobileqq"
    const val PKG_WECHAT = "com.tencent.mm"

    data class AppInfo(
        val pkg: String,
        val installed: Boolean,
        val versionName: String? = null,
        val versionCode: Long = 0L,
        /** 应用私有数据目录，通常是 /data/user/0/<pkg>，普通 App 读不到，仅用于展示 */
        val internalDataDir: String? = null,
    ) {
        /** 给人看的一句话 */
        val summary: String
            get() = when {
                !installed -> "未安装"
                versionName.isNullOrBlank() -> "已安装（版本未知）"
                else -> "已安装 · v$versionName"
            }
    }

    fun probe(ctx: Context, pkg: String): AppInfo {
        val pm = ctx.packageManager
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }

            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }

            AppInfo(
                pkg = pkg,
                installed = true,
                versionName = info.versionName,
                versionCode = code,
                internalDataDir = info.applicationInfo?.dataDir,
            )
        } catch (e: PackageManager.NameNotFoundException) {
            AppInfo(pkg = pkg, installed = false)
        } catch (e: Exception) {
            // 部分定制 ROM 上 getPackageInfo 会抛非标准异常，按未安装处理
            AppInfo(pkg = pkg, installed = false)
        }
    }

    fun probeAll(ctx: Context): Map<AccountScanner.Platform, AppInfo> = mapOf(
        AccountScanner.Platform.QQ to probe(ctx, PKG_QQ),
        AccountScanner.Platform.WECHAT to probe(ctx, PKG_WECHAT),
    )
}
