package com.stickerferry.transfer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.stickerferry.util.SafHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * 投放到 QQ。
 *
 * 这条路径机器能全包 —— 写入 QQ 表情目录即可。
 * 但写完仍需用户走一次 QQ 内的「我的表情」导入，那一步无法绕过。
 */
object QQImporter {

    /** 单个文件的处理结果 —— 批量任务靠它汇报"成功 N 个，跳过 M 个" */
    sealed class ItemResult {
        data class Ok(val name: String) : ItemResult()
        data class Skipped(val name: String, val reason: String) : ItemResult()
    }

    data class BatchReport(
        val results: List<ItemResult>,
    ) {
        val okCount: Int get() = results.count { it is ItemResult.Ok }
        val skippedCount: Int get() = results.count { it is ItemResult.Skipped }
    }

    /**
     * 批量写入。逐个 try-catch —— 一张损坏的图不能让整个任务崩掉。
     */
    suspend fun importUris(
        ctx: Context,
        qqDirUri: Uri,
        items: List<Pair<String, Uri>>,   // 文件名 to 来源 Uri
    ): BatchReport = withContext(Dispatchers.IO) {

        // 写入前检查目录是否还有权限
        if (!SafHelper.isUriValid(ctx, qqDirUri)) {
            return@withContext BatchReport(
                items.map { ItemResult.Skipped(it.first, "目录授权已失效") }
            )
        }

        val results = items.mapIndexed { index, (name, src) ->
            try {
                val bytes = ctx.contentResolver.openInputStream(src)?.use { it.readBytes() }
                    ?: return@mapIndexed ItemResult.Skipped(name, "无法读取")

                val fileName = "sticker_${String.format("%03d", index + 1)}_$name"
                val mime = if (name.lowercase().endsWith(".gif")) "image/gif" else "image/png"

                val ok = SafHelper.writeFile(ctx, qqDirUri, fileName, mime, bytes)
                if (ok) ItemResult.Ok(fileName)
                else ItemResult.Skipped(name, "写入失败")
            } catch (e: Exception) {
                ItemResult.Skipped(name, e.message ?: "未知错误")
            }
        }
        BatchReport(results)
    }

    /**
     * 写入 Bitmap（AI 生成、加字结果用这个）。
     * 静态图统一转 PNG 以保留透明通道。
     */
    suspend fun importBitmaps(
        ctx: Context,
        qqDirUri: Uri,
        bitmaps: List<Bitmap>,
    ): BatchReport = withContext(Dispatchers.IO) {
        val results = bitmaps.mapIndexed { index, bmp ->
            try {
                val bytes = ByteArrayOutputStream().use { bos ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
                    bos.toByteArray()
                }
                val name = "sticker_ai_${System.currentTimeMillis()}_$index.png"
                val ok = SafHelper.writeFile(ctx, qqDirUri, name, "image/png", bytes)
                if (ok) ItemResult.Ok(name) else ItemResult.Skipped(name, "写入失败")
            } catch (e: Exception) {
                ItemResult.Skipped("bitmap_$index", e.message ?: "未知错误")
            }
        }
        BatchReport(results)
    }
}
