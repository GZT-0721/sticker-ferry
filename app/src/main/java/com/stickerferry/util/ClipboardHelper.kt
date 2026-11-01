package com.stickerferry.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri

/**
 * 投放到微信的辅助工具。
 *
 * ⚠️ 核心原则：这里只做「把图片塞进剪贴板」和「打开微信」两件事。
 * 绝不使用 AccessibilityService 模拟点击微信界面 —— 那是外挂行为，
 * 会被微信风控识别，可能导致用户账号被限制登录。最后几步交给用户手指。
 */
object ClipboardHelper {

    /** 微信包名 */
    private const val PKG_WECHAT = "com.tencent.mm"

    /** QQ 包名 */
    private const val PKG_QQ = "com.tencent.mobileqq"

    /**
     * 把多张图片一次性塞进剪贴板。
     * Android 的 ClipData 支持带多个 Item，所以 50 张表情也是一次粘贴的事，
     * 不需要用户重复 50 遍。
     */
    fun copyImages(ctx: Context, uris: List<Uri>): Boolean {
        if (uris.isEmpty()) return false
        return try {
            val clip = ClipData.newUri(ctx.contentResolver, "sticker", uris.first())
            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }

            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(clip)
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 复制单张 Bitmap（AI 生成结果用这个） */
    fun copyBitmap(ctx: Context, bitmap: Bitmap, label: String = "sticker"): Boolean {
        return try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newBitmap(label, bitmap))
            true
        } catch (e: Exception) {
            false
        }
    }

    /** 微信是否已安装（未安装则按钮应变灰） */
    fun isInstalled(ctx: Context, pkg: String): Boolean =
        ctx.packageManager.getLaunchIntentForPackage(pkg) != null

    fun isWeChatInstalled(ctx: Context) = isInstalled(ctx, PKG_WECHAT)

    fun isQQInstalled(ctx: Context) = isInstalled(ctx, PKG_QQ)

    /**
     * 唤起微信 / QQ。
     * 只做唤起，不做任何注入。
     * 依赖 Manifest 里的 <queries> 声明，否则 Android 11+ 拿不到 launchIntent。
     */
    fun launchApp(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun launchWeChat(ctx: Context) = launchApp(ctx, PKG_WECHAT)

    fun launchQQ(ctx: Context) = launchApp(ctx, PKG_QQ)

    /** 系统分享（打包分享用） */
    fun shareFiles(ctx: Context, uris: List<Uri>, mime: String = "image/*") {
        if (uris.isEmpty()) return
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uris.first())
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = mime
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(intent, "分享表情").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
