package com.stickerferry.ai

/**
 * 提示词组装。
 *
 * 为什么不让用户从零写 prompt：
 * 好提示词有固定公式，普通用户写不出来。我们内置模板，用户只填空，
 * 成功率能从"碰运气"提升到"基本可用"。
 *
 * 公式：[风格] + [主体] + [表情动作] + [背景] + [约束]
 */
object PromptBuilder {

    /** 风格预设，对应界面上的 chips */
    val STYLES = listOf(
        "可爱卡通",
        "3D 立体",
        "像素复古",
        "手绘简笔",
        "Q 版大头",
    )

    /**
     * 把用户的一句话描述，补成完整提示词。
     *
     * 关键约束：
     *  - 「透明背景」→ 拿到图就能直接当表情包，省掉抠图
     *  - 「1:1 正方形」→ QQ / 微信表情的标准比例
     *  - 「无文字水印」→ 避免生成结果带水印
     */
    fun build(userInput: String, style: String = STYLES.first()): String {
        val subject = userInput.trim()
        return "$style 风格的表情包，" +
            "$subject，表情夸张生动，圆润造型，高饱和度，" +
            "透明背景，1:1 正方形，适合聊天使用，无文字水印"
    }

    /**
     * 内置模板，点一下直接填入输入框。
     * 这些是实测成功率较高的写法。
     */
    val TEMPLATES = listOf(
        "惊讶猫猫" to "一只圆脸的橘猫，眼睛瞪大嘴巴张成 O 型，表情震惊",
        "委屈狗狗" to "一只垂耳小狗，眼角挂着泪珠，嘴巴向下撇，柔和色彩",
        "点赞小熊" to "一只棕色小熊，双手竖起大拇指，咧嘴大笑",
        "无语柴犬" to "一只卡通柴犬，白眼半睁，嘴角下垂，一脸无奈",
    )

    /**
     * 敏感词拦截。
     *
     * 为什么要做：用真人照片生成的"表情包"涉及肖像权，
     * 尤其是公众人物，风险极高。这类请求直接拦掉，不做。
     */
    private val BLOCKED = listOf(
        "换脸", "真人照片", "明星", "网红", "领导", "主席", "总统",
    )

    /** 返回 null 表示通过，否则返回拦截原因 */
    fun checkPolicy(input: String): String? {
        val lower = input.lowercase()
        for (word in BLOCKED) {
            if (lower.contains(word)) {
                return "描述中包含了「$word」，涉及肖像权风险，本工具不提供此类生成。"
            }
        }
        return null
    }
}
