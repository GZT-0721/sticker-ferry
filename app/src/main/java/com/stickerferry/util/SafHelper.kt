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
     *
     * ⚠️ 这里**不能只按扩展名判断**。微信 emoji 目录里的表情大量是纯 md5
     * 文件名、没有后缀，按 `.png / .gif` 过滤会全部漏掉 —— 这正是早期版本
     * 扫出 0 个表情的原因之一。改成：已知图片扩展名直接收，明显不是图片的
     * 直接跳，其余（含无后缀）读前 32 字节看文件头。
     */
    fun listImages(
        ctx: Context,
        treeUri: Uri,
        maxDepth: Int = 3
    ): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(ctx, treeUri) ?: return emptyList()
        val out = mutableListOf<DocumentFile>()
        walk(ctx, root, out, 0, maxDepth)
        return out
    }

    private fun walk(
        ctx: Context,
        dir: DocumentFile,
        out: MutableList<DocumentFile>,
        depth: Int,
        max: Int,
    ) {
        if (depth > max) return
        val children = try {
            dir.listFiles()
        } catch (e: Exception) {
            return
        }
        for (f in children) {
            if (f.isDirectory) {
                walk(ctx, f, out, depth + 1, max)
            } else if (looksLikeImage(ctx, f)) {
                out.add(f)
            }
        }
    }

    /** 明确是图片的扩展名 */
    private val FAST_IMAGE_EXT = setOf("png", "gif", "jpg", "jpeg", "webp", "bmp")

    /** 明确不是图片的扩展名，省掉一次读流 */
    private val SKIP_EXT = setOf(
        "db", "db-wal", "db-shm", "sqlite", "xml", "json", "txt", "so",
        "ini", "log", "tmp", "lock", "cfg", "conf", "html", "htm",
        "js", "css", "proto", "idx", "zip", "apk", "ttf", "otf",
    )

    private fun looksLikeImage(ctx: Context, f: DocumentFile): Boolean {
        val name = f.name ?: return false
        val dot = name.lastIndexOf('.')
        if (dot in 1 until name.length - 1) {
            val ext = name.substring(dot + 1).lowercase()
            if (ext in FAST_IMAGE_EXT) return true
            if (ext in SKIP_EXT) return false
        }
        // 无扩展名 / 未知扩展名 —— 读文件头判断
        val uri = f.uri
        return sniffMime(ctx, uri) != null
    }

    /**
     * 读文件前 32 字节判断是不是能显示的图片，返回 MIME，认不出返回 null。
     *
     * 这一层特意不复用 data 包里的 ImageSniffer —— SAF 的输入是 Uri，
     * 必须走 ContentResolver 开流，跟 File 的读法不同。判断逻辑保持一致。
     */
    fun sniffMime(ctx: Context, uri: Uri): String? {
        val head = ByteArray(32)
        val n = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.read(head) } ?: return null
        } catch (e: Exception) {
            return null
        }
        if (n < 8) return null

        fun at(i: Int): Int = head[i].toInt() and 0xFF

        if (n >= 6 &&
            at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38 &&
            (at(4) == 0x37 || at(4) == 0x39) && at(5) == 0x61
        ) return "image/gif"

        if (n >= 8 &&
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 &&
            at(4) == 0x0D && at(5) == 0x0A && at(6) == 0x1A && at(7) == 0x0A
        ) return "image/png"

        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return "image/jpeg"

        if (n >= 12 &&
            at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
            at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50
        ) return "image/webp"

        return null
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
