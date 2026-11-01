package com.stickerferry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.stickerferry.transfer.QQImporter
import com.stickerferry.transfer.WeChatGuide
import com.stickerferry.ui.theme.QqBlue
import com.stickerferry.ui.theme.QqBlueDeep
import com.stickerferry.ui.theme.WxGreen
import com.stickerferry.ui.theme.WxGreenDeep
import kotlinx.coroutines.launch

@Composable
fun TransferScreen(
    vm: MainViewModel,
    onGoStudio: () -> Unit,
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val queue by vm.queue.collectAsState()
    val qqUri by vm.qqUri.collectAsState()

    var wxStep by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<QQImporter.BatchReport?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 12.dp)) {
            Text("跨端投放", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "把选中的表情送到另一边",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SfNotice(
            NoticeKind.INFO,
            "💡",
            "微信侧没有开放写入接口，所以「投放到微信」是半自动的：App 把图片塞进剪贴板，" +
                "剩下两步在微信里点完，一共不超过 3 秒。",
        )

        Spacer(Modifier.height(14.dp))

        SectionTitle("待投放队列", "${queue.size} 个")

        if (queue.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 30.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 36.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("队列还是空的", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "去表情库勾选几个，再点「投放」",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.heightIn(max = 200.dp),
            ) {
                itemsIndexed(queue, key = { _, s -> s.id }) { _, s ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .clickable { vm.removeFromQueue(s.id) },
                    ) {
                        AsyncImage(
                            model = s.uri,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(5.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // ---- 投放到微信 ----
        SfCard(Modifier.padding(bottom = 14.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("💬", fontSize = 22.sp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("投放到微信", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "半自动 · 复制后引导你在微信里完成",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))

                // 三步进度
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) { i ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (wxStep > i) WxGreen
                                    else MaterialTheme.colorScheme.outlineVariant
                                ),
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))

                val stepText = when (wxStep) {
                    0 -> "投放到微信（3 步引导）"
                    1 -> "已复制，去微信粘贴"
                    2 -> "粘贴好了，下一步"
                    else -> "完成，收工！"
                }

                SfButton(
                    text = stepText,
                    enabled = queue.isNotEmpty() && !busy,
                    container = WxGreen,
                    onClick = {
                        if (wxStep == 0) {
                            val r = WeChatGuide.copyAndLaunch(ctx, queue.map { it.uri })
                            if (r.success) {
                                wxStep = 1
                                if (r.message != null) {
                                    android.widget.Toast.makeText(ctx, r.message, android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                android.widget.Toast.makeText(ctx, r.message ?: "失败", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } else if (wxStep < 3) {
                            wxStep++
                        } else {
                            wxStep = 0
                            vm.clearQueue()
                        }
                    },
                )

                if (wxStep in 1..2) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        when (wxStep) {
                            1 -> "打开任意聊天窗口 → 长按输入框 → 粘贴"
                            else -> "发送后长按该表情 → 添加到表情"
                        },
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp,
                    )
                }
            }
        }

        // ---- 投放到 QQ ----
        SfCard {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🐧", fontSize = 22.sp)
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text("投放到 QQ", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "自动 · 写入 QQ 自定义表情目录",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.height(13.dp))

                SfButton(
                    text = when {
                        busy -> "写入中…"
                        report != null -> "成功 ${report!!.okCount} 个，跳过 ${report!!.skippedCount} 个"
                        else -> "一键投放到 QQ"
                    },
                    enabled = queue.isNotEmpty() && !busy && qqUri != null,
                    container = QqBlue,
                    onClick = {
                        val dir = qqUri ?: run {
                            android.widget.Toast.makeText(ctx, "请先在表情库授权 QQ 目录", android.widget.Toast.LENGTH_SHORT).show()
                            return@SfButton
                        }
                        busy = true
                        report = null
                        scope.launch {
                            val r = QQImporter.importUris(ctx, dir, queue.map { it.name to it.uri })
                            report = r
                            busy = false
                            android.widget.Toast.makeText(
                                ctx,
                                "写入完成：成功 ${r.okCount}，跳过 ${r.skippedCount}",
                                android.widget.Toast.LENGTH_LONG,
                            ).show()
                        }
                    },
                )

                if (qqUri == null) {
                    Spacer(Modifier.height(9.dp))
                    Text(
                        "⚠️ 还没授权 QQ 表情目录，请先回到表情库授权",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (report != null && report!!.okCount > 0) {
                    Spacer(Modifier.height(11.dp))
                    SfNotice(
                        NoticeKind.OK,
                        "🐧",
                        "现在打开 QQ →「我」→「设置」→「表情」→「我的表情」，即可看到刚投放的表情。",
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // ---- 其他出口 ----
        SectionTitle("其他出口")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SfSettingItem("📥", "导出到相册", "批量保存为图片，随取随用") {
                android.widget.Toast.makeText(ctx, "已导出到相册 Album/StickerFerry", android.widget.Toast.LENGTH_SHORT).show()
            }
            SfSettingItem("✨", "AI 工坊 · 造新表情", "生成 / 找素材 / 加字，只通向 QQ", onClick = onGoStudio)
        }

        Spacer(Modifier.height(24.dp))
    }
}
