package com.stickerferry.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * 感知哈希去重。
 *
 * 为什么用 dHash 而不是直接比文件大小 / MD5：
 * 同一个表情常被转发多次，尺寸、压缩率都不同，MD5 完全对不上。
 * dHash 看的是"图像内容的结构特征"，缩放过的图也能识别为同一张。
 */
object ImageHash {

    /**
     * 计算 dHash（差值哈希）。
     * 做法：缩到 9x8 灰度图，逐行比较相邻像素亮度，得到 64 位指纹。
     */
    fun dHash(ctx: Context, uri: Uri): String? {
        return try {
            val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            } ?: return null

            val small = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
            val sb = StringBuilder(64)
            for (y in 0 until 8) {
                for (x in 0 until 8) {
                    val left = gray(small.getPixel(x, y))
                    val right = gray(small.getPixel(x + 1, y))
                    sb.append(if (left > right) '1' else '0')
                }
            }
            small.recycle()
            if (small !== bitmap) bitmap.recycle()
            sb.toString()
        } catch (e: Exception) {
            // 动图 / 损坏文件可能解码失败，跳过即可
            null
        }
    }

    private fun gray(color: Int): Int {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000
    }

    /**
     * 汉明距离：两个哈希不同的位数。
     * 距离 ≤ 阈值 视为重复。阈值 5 是经验值，太小会漏，太大容易误判。
     */
    fun hamming(a: String, b: String): Int {
        if (a.length != b.length) return Int.MAX_VALUE
        var d = 0
        for (i in a.indices) if (a[i] != b[i]) d++
        return d
    }

    /**
     * 把一组表情按相似度分组。
     * 返回：每组是一个重复集合（组内至少 2 个）。默认保留每组第一个。
     */
    fun findDuplicates(
        stickers: List<Pair<String, String?>>,   // id to hash
        threshold: Int = 5
    ): List<List<String>> {
        val groups = mutableListOf<List<String>>()
        val used = mutableSetOf<String>()

        val valid = stickers.filter { it.second != null }
        for (i in valid.indices) {
            val (idA, hashA) = valid[i]
            if (idA in used) continue

            val group = mutableListOf(idA)
            for (j in i + 1 until valid.size) {
                val (idB, hashB) = valid[j]
                if (idB in used) continue
                if (hamming(hashA!!, hashB!!) <= threshold) {
                    group.add(idB)
                }
            }
            if (group.size > 1) {
                used.addAll(group)
                groups.add(group)
            }
        }
        return groups
    }
}
