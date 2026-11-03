package com.stickerferry.data

import java.io.File

/**
 * 按文件头（魔数）判断是不是图片，**不看扩展名**。
 *
 * 为什么必须这样 —— 这是之前「扫描结果是 0」最直接的元凶：
 *
 * 微信的 emoji 目录里，表情文件的文件名是纯 md5，**根本没有后缀**。
 * 形如 `MicroMsg/<md5>/emoji/d41d8cd98f00b204e9800998ecf8427e`。
 * 按 `.png / .gif` 后缀过滤，这些文件会被全部丢弃；
 * 一旦某个账号的表情全是这种无后缀文件，扫出来就是 0。
 *
 * QQ 侧也有同类问题，部分缓存文件被写成 `.dat` 或无后缀。
 *
 * 所以正确的做法是：读前几十个字节，认文件签名。成本很低（一次短读），
 * 但能覆盖扩展名缺失、扩展名写错、扩展名被改成 .dat 等所有情况。
 */
object ImageSniffer {

    enum class Kind(val anim: Boolean) {
        GIF(true),
        PNG(false),
        JPEG(false),
        WEBP(false),
        BMP(false),
        UNKNOWN(false),
    }

    /** 读前 32 字节即可覆盖全部需要的签名 */
    private const val HEAD_LEN = 32

    /** 超过这个大小的一定不是表情包，先挡掉，省一次 IO */
    private const val MAX_BYTES = 8L * 1024 * 1024

    /**
     * 嗅探一个文件。
     * 任何异常都归为 UNKNOWN —— 一张坏图不该让整个扫描崩掉。
     */
    fun sniff(f: File): Kind {
        if (!f.isFile) return Kind.UNKNOWN
        val len = try { f.length() } catch (e: Exception) { return Kind.UNKNOWN }
        // 少于 8 字节不可能是有效图片头
        if (len < 8L || len > MAX_BYTES) return Kind.UNKNOWN

        val head = ByteArray(HEAD_LEN)
        val n = try {
            f.inputStream().use { it.read(head) }
        } catch (e: Exception) {
            return Kind.UNKNOWN
        }
        return sniffBytes(head, n.coerceAtLeast(0))
    }

    /**
     * 按字节签名判断。抽成独立方法，方便单测，
     * 也方便将来从 stream / Uri 读的时候复用同一套判断。
     */
    fun sniffBytes(b: ByteArray, len: Int): Kind {
        if (len < 2) return Kind.UNKNOWN

        fun at(i: Int): Int = b[i].toInt() and 0xFF

        // GIF87a / GIF89a
        if (len >= 6 &&
            at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x38 &&
            (at(4) == 0x37 || at(4) == 0x39) && at(5) == 0x61
        ) return Kind.GIF

        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (len >= 8 &&
            at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47 &&
            at(4) == 0x0D && at(5) == 0x0A && at(6) == 0x1A && at(7) == 0x0A
        ) return Kind.PNG

        // JPEG: FF D8 FF
        if (at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF) return Kind.JPEG

        // WEBP: "RIFF" .... "WEBP"
        if (len >= 12 &&
            at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 &&
            at(8) == 0x57 && at(9) == 0x45 && at(10) == 0x42 && at(11) == 0x50
        ) return Kind.WEBP

        // BMP: "BM"，再用文件头里记录的尺寸字段交叉验证，避免误判纯文本
        if (len >= 6 && at(0) == 0x42 && at(1) == 0x4D) {
            val declared = at(2) or (at(3) shl 8) or (at(4) shl 16) or (at(5) shl 24)
            if (declared > 0) return Kind.BMP
        }

        return Kind.UNKNOWN
    }

    /**
     * APNG 也是 PNG，但可能是动图。这里不做深度判断 ——
     * 动画标记只影响「动图」筛选，判错的代价很小，
     * 不值得为它多读一遍文件。
     */
    fun isAnimated(f: File, kind: Kind): Boolean = kind.anim
}
