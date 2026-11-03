package com.stickerferry.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 诊断页。
 *
 * 存在的意义只有一个：把「为什么扫不到」从猜测变成事实。
 *
 * 之前排查这类问题全靠来回问「你手机什么系统」「QQ 装了没」「有没有那个目录」，
 * 一轮就要一天。现在点一下生成报告，每条候选路径是「不存在」还是
 * 「存在但读不了」都写得明明白白，一眼就能定位。
 */
@Composable
fun DiagnoseScreen(vm: MainViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val text by vm.diagnoseText.collectAsState()
    val running by vm.diagnosing.collectAsState()

    // 进来就自动跑一次，用户不用多点一下
    LaunchedEffect(Unit) {
        if (text.isBlank()) vm.runDiagnose()
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 18.dp, top = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Text("‹", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f)) {
                Text("诊断报告", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "每条候选路径的真实状态",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (running) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 18.dp),
        ) {
            SfNotice(
                NoticeKind.INFO, "📋",
                "报告里全是本机路径，不含任何图片内容。排查问题时把这段发出来就行。",
            )

            Spacer(Modifier.height(11.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    SfButton(
                        text = if (running) "诊断中…" else "🔍 重新诊断",
                        enabled = !running,
                        onClick = { vm.runDiagnose() },
                    )
                }
                Box(Modifier.weight(1f)) {
                    SfButton(
                        text = "📋 复制报告",
                        container = Color(0xFF22A06B),
                        enabled = text.isNotBlank(),
                        onClick = {
                            copyToClipboard(ctx, text)
                            Toast.makeText(ctx, "已复制，可直接粘贴发送", Toast.LENGTH_SHORT).show()
                        },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF12161F))
                    .border(1.dp, Color(0xFF2A3242), RoundedCornerShape(14.dp)),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(13.dp),
                ) {
                    Text(
                        text = text.ifBlank { if (running) "正在扫描…" else "还没有报告" },
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFCBD5E8),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

private fun copyToClipboard(ctx: Context, text: String) {
    try {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("表情摆渡诊断报告", text))
    } catch (e: Exception) {
        // 剪贴板被系统限制时静默失败，用户还能长按选中文本
    }
}
