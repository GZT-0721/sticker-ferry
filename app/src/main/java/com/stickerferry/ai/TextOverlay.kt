package com.stickerferry.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * 图上加字 —— 纯本地 Canvas 绘制，零依赖零成本。
 *
 * 这是 AI 工坊里最稳的一个模块：不需要任何网络服务，立刻能用。
 * 建议作为第一个落地的功能。
 */
object TextOverlay {

    /** 文字位置 */
    enum class Position { TOP, MIDDLE, BOTTOM }

    /**
     * 把文字烧录到图片上，带黑色描边。
     *
     * 描边是表情包加字的关键：无论底图明暗，字都清晰可读。
     * 这是所有表情包工具的标准做法。
     *
     * ⚠️ 绘制顺序必须「先描边、后填字」—— 反过来字会被描边盖住。
     */
    fun draw(
        src: Bitmap,
        text: String,
        position: Position = Position.BOTTOM,
        textColor: Int = Color.WHITE,
        strokeColor: Int = Color.BLACK,
        maxWidthRatio: Float = 0.9f,
    ): Bitmap {
        if (text.isBlank()) return src

        // 用 ARGB_8888 才能保证有透明通道
        val out = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        // 字号跟随图片尺寸，保证不同大小的图看起来协调
        var size = out.width * 0.12f

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            strokeJoin = Paint.Join.ROUND
        }

        // 文字太长就自动缩小字号，避免超出边界
        val maxWidth = out.width * maxWidthRatio
        while (paint.measureText(text) > maxWidth && size > out.width * 0.04f) {
            size *= 0.92f
            paint.textSize = size
        }

        val cx = out.width / 2f
        val cy = when (position) {
            Position.TOP -> size * 1.3f
            Position.MIDDLE -> out.height / 2f + size * 0.35f
            Position.BOTTOM -> out.height - size * 0.5f
        }

        // 第一步：画描边（粗线条）
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = size * 0.16f
        paint.color = strokeColor
        canvas.drawText(text, cx, cy, paint)

        // 第二步：填字（细实心）
        paint.style = Paint.Style.FILL
        paint.color = textColor
        canvas.drawText(text, cx, cy, paint)

        return out
    }

    /** 可选文字颜色，对应界面的色块 */
    val COLORS = listOf(
        Color.WHITE,
        0xFFFFE066.toInt(),   // 明黄
        0xFFFF6B6B.toInt(),   // 珊瑚红
        0xFF4ECDC4.toInt(),   // 青绿
        0xFFA29BFE.toInt(),   // 淡紫
        0xFFFF9F43.toInt(),   // 橙
    )
}
