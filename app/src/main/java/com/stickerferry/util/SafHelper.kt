package com.stickerferry.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * SAF（Storage Access Framework）工具。
 *
 * 为什么必须用它：Android 11+ 下 App 无法直接访问 Android/data/ 目录，
 * 硬编码路径会被系统拒绝。只能让用户在系统选择器里手动点选目录，
 * 我们把返回的 Uri 持久化保存，才能长期读写。
 */
object SafHelper {

    /** 支持的图片扩展名 */
    private val IMAGE_EXT = listOf(".gif", ".png", ".jpg", ".jpeg", ".webp", ".bmp")

    /**
     * 申请持久化读写权限。
     * 不调用这个，App 重启后授权就失效了，用户得重新点一遍。
     */
    fun persistPermission(ctx: Context, uri: Uri, write: Boolean = true): Boolean {
        return try {
            var flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            if (write) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
            true
        } catch (e: SecurityException) {
            // 某些目录不支持写权限，能读也够用
            false
        }
    }

    /** 检查已保存的授权是否还有效（比如微信重装后目录就没了） */
    fun isUriValid(ctx: Context, uri: Uri): Boolean {
        return try {
            DocumentFile.fromTreeUri(ctx, uri)?.exists() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 递归列出目录下所有图片文件。
     * 表情目录可能存在多层嵌套，所以要递归。
     */
    fun listImages(
        ctx: Context,
        treeUri: Uri,
        maxDepth: Int = 3
    ): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return emptyList()
        val out = mutableListOf<DocumentFile>()
        walk(root, out, 0, maxDepth)
        return out
    }

    private fun walk(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int, max: Int) {
        if (depth > max) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }
        for (f in children) {
            if (f.isDirectory) {
                walk(f, out, depth + 1, max)
            } else {
                val n = f.name?.lowercase() ?: continue
                if (IMAGE_EXT.any { n.endsWith(it) }) out.add(f)
            }
        }
    }

    /** 判断文件是否为动图 */
    fun isAnimated(name: String): Boolean =
        name.lowercase().endsWith(".gif") || name.lowercase().endsWith(".webp")

    /**
     * 在指定目录下创建文件并写入字节。
     * 失败返回 null，调用方负责记录并跳过（不能让单个失败中断批量任务）。
     */
    fun writeFile(ctx: Context, dirUri: Uri, fileName: String, mime: String, bytes: ByteArray): Boolean {
        return try {
            val dir = DocumentFile.fromTreeUri(ctx, dirUri) ?: return false
            // 重名时自动加时间戳后缀
            val finalName = if (dir.findFile(fileName) != null) {
                val base = fileName.substringBeforeLast('.')
                val ext = fileName.substringAfterLast('.', "")
                "${base}_${System.currentTimeMillis()}.$ext"
            } else fileName

            val target = dir.createFile(mime, finalName) ?: return false
            ctx.contentResolver.openOutputStream(target.uri!!)?.use { out ->
                out.write(bytes)
            }
            true
        } catch (e: Exception) {
            false
        }
    }
}
