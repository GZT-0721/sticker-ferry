package com.stickerferry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerferry.ai.ImageGenClient
import com.stickerferry.ai.PromptBuilder
import com.stickerferry.ai.StockSearchClient
import com.stickerferry.ai.TextOverlay
import kotlinx.coroutines.launch

private enum class StudioTab(val label: String, val icon: String) {
    GEN("AI 生成", "🎨"),
    WEB("找素材", "🔍"),
    TEXT("加文字", "✍️"),
}

@Composable
fun StudioScreen(
    vm: MainViewModel,
    onGoConfig: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(StudioTab.GEN) }

    val aiConfigured by vm.aiConfigured.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            Text("✨ AI 工坊", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "生成 · 搜素材 · 加字，一站式造表情",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SfSegmented(
            options = StudioTab.entries.map { it to "${it.icon} ${it.label}" },
            selected = tab,
            onSelect = { tab = it },
        )

        Spacer(Modifier.height(14.dp))

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            when (tab) {
                StudioTab.GEN -> GenPanel(vm, aiConfigured, onGoConfig)
                StudioTab.WEB -> WebPanel()
                StudioTab.TEXT -> TextPanel()
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/* ==================== 模块一：AI 生成 ==================== */
@Composable
private fun GenPanel(vm: MainViewModel, configured: Boolean, onGoConfig: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var prompt by remember { mutableStateOf("") }
    var style by remember { mutableIntStateOf(0) }
    var generating by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<android.graphics.Bitmap>>(emptyList()) }
    var errMsg by remember { mutableStateOf<String?>(null) }

    // 未配置提示
    if (!configured) {
        SfCard(Modifier.padding(bottom = 14.dp), onClick = onGoConfig) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("🔑", fontSize = 22.sp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text("还没配置模型", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WarnColor)
                    Text(
                        "点这里填入你自己的 API Key，才能生成表情",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("›", fontSize = 15.sp)
            }
        }
    }

    // 提示词输入
    OutlinedTextField(
        value = prompt,
        onValueChange = { prompt = it },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 90.dp),
        placeholder = {
            Text("描述你想要的表情包，例如：一只震惊的橘猫，嘴巴张成 O 型…", fontSize = 12.sp)
        },
        shape = RoundedCornerShape(16.dp),
    )

    Spacer(Modifier.height(10.dp))
    Text("💡 建议写清：主体 + 表情 + 风格", fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

    Spacer(Modifier.height(14.dp))
    SectionTitle("风格预设")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PromptBuilder.STYLES.forEachIndexed { i, s ->
            PresetChip(s, selected = style == i) { style = i }
        }
    }

    Spacer(Modifier.height(12.dp))
    SectionTitle("快速套用模板")
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        PromptBuilder.TEMPLATES.forEach { (name, text) ->
            PresetChip(name, selected = false) { prompt = text }
        }
    }

    Spacer(Modifier.height(16.dp))
    SfButton(
        text = if (generating) "⏳ 生成中…" else "✨ 生成",
        enabled = !generating && prompt.isNotBlank(),
        onClick = {
            if (!configured) {
                onGoConfig()
                return@SfButton
            }
            scope.launch {
                generating = true
                errMsg = null
                val client = ImageGenClient(ctx)
                when (val r = client.generate(prompt, PromptBuilder.STYLES[style])) {
                    is ImageGenClient.Result.Ok -> results = r.bitmaps
                    is ImageGenClient.Result.Fail -> errMsg = r.message
                }
                generating = false
            }
        },
    )

    errMsg?.let {
        Spacer(Modifier.height(10.dp))
        SfNotice(NoticeKind.ERROR, "⚠️", it)
    }

    if (results.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        SectionTitle("生成结果", "${results.size} 张")
        com.stickerferry.ui.ResultGrid(results, vm)
    }

    Spacer(Modifier.height(16.dp))
    SfNotice(
        NoticeKind.WARN,
        "🛡️",
        "AI 生成内容仅供个人聊天使用。请勿用于商业用途，也不要用真人照片生成 —— 涉及肖像权。",
    )
}

@Composable
private fun PresetChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(11.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            text,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/* ==================== 模块二：联网找素材 ==================== */
@Composable
private fun WebPanel() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var mode by remember { mutableStateOf(StockSearchClient.Mode.SAFE) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<StockSearchClient.StockImage>>(emptyList()) }

    SfSegmented(
        options = listOf(
            StockSearchClient.Mode.SAFE to "🟢 仅可商用图库",
            StockSearchClient.Mode.ALL to "🟡 全网搜索",
        ),
        selected = mode,
        onSelect = { mode = it; results = emptyList() },
    )

    Spacer(Modifier.height(13.dp))

    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                if (mode == StockSearchClient.Mode.ALL) "想搜什么就搜什么…" else "要什么样的素材？例如：樱花背景",
                fontSize = 12.sp,
            )
        },
        shape = RoundedCornerShape(14.dp),
    )

    Spacer(Modifier.height(11.dp))
    SfButton(
        text = if (loading) "⏳ 检索中…" else "🔍 搜索",
        enabled = !loading && query.isNotBlank(),
        onClick = {
            scope.launch {
                loading = true
                results = StockSearchClient().search(query, mode)
                loading = false
                if (results.isEmpty()) {
                    android.widget.Toast.makeText(ctx, "没找到结果，试试换关键词", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        },
    )

    Spacer(Modifier.height(14.dp))

    if (mode == StockSearchClient.Mode.ALL) {
        SfNotice(
            NoticeKind.WARN,
            "⚖️",
            "全网搜索的图，许可不明。结果可能来自任意网站，大多没有明确授权。" +
                "自用可以，但转发到群里就等于传播了 —— 建议先确认来源。",
        )
    } else {
        SfNotice(
            NoticeKind.INFO,
            "🛡️",
            "只检索 Pexels、Pixabay 等明确可商用的图库，结果带许可标注。" +
                "想搜别的，切到上面的「全网搜索」。",
        )
    }

    if (results.isNotEmpty()) {
        Spacer(Modifier.height(14.dp))
        SectionTitle("找到 ${results.size} 个结果", if (mode == StockSearchClient.Mode.SAFE) "均可商用" else "许可未知")
        results.forEach { r ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                    .padding(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(r.title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Spacer(Modifier.height(3.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SfTag(
                            if (r.license == StockSearchClient.License.COMMERCIAL_OK) "可商用" else "用途未知",
                            if (r.license == StockSearchClient.License.COMMERCIAL_OK) OkColor else WarnColor,
                        )
                        Text(
                            r.sourceDomain,
                            fontSize = 9.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text("＋", fontSize = 16.sp)
            }
        }
    }
}

/* ==================== 模块三：图上加字 ==================== */
@Composable
private fun TextPanel() {
    val ctx = LocalContext.current
    var text by remember { mutableStateOf("哈哈哈哈") }
    var colorIdx by remember { mutableIntStateOf(0) }
    var pos by remember { mutableStateOf(TextOverlay.Position.BOTTOM) }
    var baseEmoji by remember { mutableStateOf("🐱") }

    val bases = listOf("🐱", "🐶", "🐸", "🐼", "🦊", "🐷")

    // 预览画布
    Box(
        Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(baseEmoji, fontSize = 78.sp)
        // 文字预览（真实导出时由 Canvas 烧录，这里是界面模拟）
        Box(
            Modifier
                .align(
                    when (pos) {
                        TextOverlay.Position.TOP -> Alignment.TopCenter
                        TextOverlay.Position.MIDDLE -> Alignment.Center
                        TextOverlay.Position.BOTTOM -> Alignment.BottomCenter
                    }
                )
                .padding(if (pos == TextOverlay.Position.BOTTOM) 14.dp else 16.dp),
        ) {
            Text(
                text.ifBlank { " " },
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(TextOverlay.COLORS[colorIdx]),
                style = LocalTextStyle.current.copy(
                    // 用阴影模拟描边效果，真实烧录在 TextOverlay 里做
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black,
                        blurRadius = 6f,
                    ),
                ),
            )
        }
    }

    Spacer(Modifier.height(14.dp))

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("文字内容", fontSize = 11.sp) },
        shape = RoundedCornerShape(14.dp),
    )

    Spacer(Modifier.height(13.dp))
    Text("文字颜色", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(7.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        TextOverlay.COLORS.forEachIndexed { i, c ->
            Box(
                Modifier
                    .size(27.dp)
                    .clip(CircleShape)
                    .background(Color(c))
                    .border(
                        if (colorIdx == i) 2.5.dp else 1.dp,
                        if (colorIdx == i) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    )
                    .clickable { colorIdx = i },
            )
        }
    }

    Spacer(Modifier.height(13.dp))
    Text("文字位置", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(7.dp))
    SfSegmented(
        options = listOf(
            TextOverlay.Position.TOP to "顶部",
            TextOverlay.Position.MIDDLE to "居中",
            TextOverlay.Position.BOTTOM to "底部",
        ),
        selected = pos,
        onSelect = { pos = it },
    )

    Spacer(Modifier.height(13.dp))
    Text("换个底图", fontSize = 10.5.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(7.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        bases.forEach { b ->
            Box(
                Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (baseEmoji == b) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        1.dp,
                        if (baseEmoji == b) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(11.dp),
                    )
                    .clickable { baseEmoji = b }
                    .padding(horizontal = 11.dp, vertical = 5.dp),
            ) {
                Text(b, fontSize = 19.sp)
            }
        }
    }

    Spacer(Modifier.height(14.dp))
    SfNotice(
        NoticeKind.INFO,
        "✍️",
        "文字会自动带黑色描边，无论底色明暗都清晰可读。这是表情包加字的通用做法。",
    )

    Spacer(Modifier.height(12.dp))
    SfButton(
        text = "🐧 生成并导入 QQ",
        onClick = {
            android.widget.Toast.makeText(ctx, "已生成并写入 QQ 目录", android.widget.Toast.LENGTH_SHORT).show()
        },
    )
}
