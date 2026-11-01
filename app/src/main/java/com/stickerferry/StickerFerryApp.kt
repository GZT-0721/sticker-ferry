package com.stickerferry

import android.app.Application

/**
 * 应用入口。
 *
 * 刻意什么都不初始化 —— 不接任何统计、上报、崩溃收集 SDK。
 * 表情是私密数据，"不联网"要从架构上保证，而不是靠承诺。
 */
class StickerFerryApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 无第三方 SDK 初始化，保持干净
    }
}
