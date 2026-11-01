package com.stickerferry.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * 统一数据源。界面只跟它打交道，不关心图是从哪个目录来的。
 */
class StickerRepository(private val ctx: Context) {

    /**
     * 并行扫描两个来源。
     * 用 async 并行是因为目录遍历可能很慢（几百个文件的目录要走好几秒），
     * 串行会白白多等一倍时间。
     */
    suspend fun scanAll(
        qqUri: Uri?,
        wechatUri: Uri?,
    ): ScanReport = coroutineScope {
        val qqTask = async(Dispatchers.IO) {
            if (qqUri == null) emptyList<Sticker>() to null
            else QQSource.scan(ctx, qqUri)
        }
        val wxTask = async(Dispatchers.IO) {
            if (wechatUri == null) emptyList<Sticker>() to null
            else WeChatSource.scan(ctx, wechatUri)
        }

        val (qqList, qqErr) = qqTask.await()
        val (wxList, wxErr) = wxTask.await()

        ScanReport(
            qq = qqList.sortedByDescending { it.addedAt },
            wechat = wxList.sortedByDescending { it.addedAt },
            qqError = qqErr,
            wechatError = wxErr,
        )
    }

    /** 导出到相册（实际写入由 MediaStore 完成，这里只做筛选） */
    suspend fun filterExportable(stickers: List<Sticker>): List<Sticker> =
        withContext(Dispatchers.IO) {
            stickers.filter { it.sizeBytes > 0 }
        }
}
