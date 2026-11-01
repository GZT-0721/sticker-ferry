package com.stickerferry.transfer

import android.content.Context
import android.net.Uri
import com.stickerferry.util.ClipboardHelper

/**
 * 投放到微信 —— 半自动三步引导。
 *
 * ┌─────────────────────────────────────────────────────────┐
 * │ 为什么必须半自动：微信没有开放任何写入接口             │
 * │                                                          │
 * │ 绕过它只有两条路，两条都走不通：                        │
 * │   ① 解密 WCDB 数据库 → 逆向工程，微信一更新就失效        │
 * │   ② 无障碍服务模拟点击 → 外挂行为，风控会限制账号登录    │
 * │                                                          │
 * │ 所以我们只做能做的：把图片塞进剪贴板，剩下两步交给用户。 │
 * └─────────────────────────────────────────────────────────┘
 */
object WeChatGuide {

    /** 三步流程。每步标明"谁来做"。 */
    sealed class Step(val index: Int, val title: String, val detail: String, val byMachine: Boolean) {
        object Copy : Step(1, "复制图片", "正在把选中的表情写入剪贴板…", true)
        object Paste : Step(2, "去微信粘贴", "打开任意聊天窗口，长按输入框选「粘贴」", false)
        object AddToFav : Step(3, "添加到我的表情", "发送后长按该表情，选「添加到表情」", false)

        companion object {
            val ALL = listOf(Copy, Paste, AddToFav)
        }
    }

    /**
     * 第一步：把图片批量写入剪贴板，然后唤起微信。
     *
     * 批量投放的关键：50 张图一次性全塞进剪贴板，
     * 用户粘贴时是一批图一起进去，不需要重复 50 遍。
     *
     * 所谓"三步"是指流程三步，不是每个表情三步。
     */
    fun copyAndLaunch(ctx: Context, uris: List<Uri>): CopyResult {
        if (uris.isEmpty()) return CopyResult(false, "队列为空")

        val copied = ClipboardHelper.copyImages(ctx, uris)
        if (!copied) return CopyResult(false, "剪贴板写入失败")

        val launched = ClipboardHelper.isWeChatInstalled(ctx) &&
            ClipboardHelper.launchWeChat(ctx)

        return CopyResult(
            success = true,
            message = if (ClipboardHelper.isWeChatInstalled(ctx)) null else "未检测到微信",
            launched = launched,
        )
    }

    data class CopyResult(
        val success: Boolean,
        val message: String? = null,
        val launched: Boolean = false,
    )

    /** 第二步、第三步无法由程序校验（我们感知不到用户在微信里做了什么），只做引导 */
    fun canVerify(): Boolean = false

    /**
     * 微信是否可用（未安装则按钮应变灰，而不是点了没反应）
     */
    fun isAvailable(ctx: Context): Boolean = ClipboardHelper.isWeChatInstalled(ctx)
}
