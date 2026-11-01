package com.stickerferry.data

import android.content.Context
import android.net.Uri
import com.stickerferry.util.SafHelper

/**
 * QQ 表情目录读取。
 *
 * 已知路径（供用户在选择器里定位用）：
 *   自定义表情  /Android/data/com.tencent.mobileqq/files/expression/
 *   聊天收到    .../Tencent/QQfile_recv/CustomFace/
 *   收藏单张    内部存储/Tencent/QQ_Favorite/
 */
object QQSource {

    /** 常见路径提示，界面上展示给用户参考 */
    val PATH_HINTS = listOf(
        "Android/data/com.tencent.mobileqq/files/expression",
        "Android/data/com.tencent.mobileqq/Tencent/QQfile_recv/CustomFace",
        "Tencent/QQ_Favorite",
    )

    /**
     * 扫描指定目录（用户通过 SAF 授权的 Uri）。
     * 读不到就返回空列表并带上错误信息，让界面能显示"部分成功"状态。
     */
    fun scan(ctx: Context, treeUri: Uri): Pair<List<Sticker>, String?> {
        return try {
            val files = SafHelper.listImages(ctx, treeUri)
            val list = files.mapNotNull { f ->
                val uri = f.uri
                val name = f.name ?: return@mapNotNull null
                Sticker(
                    id = uri.toString(),
                    uri = uri,
                    name = name,
                    source = Source.QQ,
                    isAnim = SafHelper.isAnimated(name),
                    sizeBytes = f.length(),
                    addedAt = f.lastModified(),
                )
            }
            list to null
        } catch (e: Exception) {
            emptyList<Sticker>() to (e.message ?: "QQ 目录读取失败")
        }
    }
}
