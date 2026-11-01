package com.stickerferry.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerferry.ui.theme.QqBlue
import com.stickerferry.transfer.QQImporter
import kotlinx.coroutines.launch

/**
 * AI 生成结果网格。点选后可批量写入 QQ。
 */
@Composable
fun ResultGrid(bitmaps: List<Bitmap>, vm: MainViewModel) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val qqUri by vm.qqUri.collectAsState()

    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var importing by remember { mutableStateOf(false) }

    Column {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            bitmaps.take(2).forEachIndexed { i, bmp ->
                ResultCell(
                    modifier = Modifier.weight(1f),
                    bitmap = bmp,
                    picked = i in picked,
                    onClick = {
                        picked = if (i in picked) picked - i else picked + i
                    },
                )
            }
        }
        if (bitmaps.size > 2) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                bitmaps.drop(2).take(2).forEachIndexed { idx, bmp ->
                    val i = idx + 2
                    ResultCell(
                        modifier = Modifier.weight(1f),
                        bitmap = bmp,
                        picked = i in picked,
                        onClick = {
                            picked = if (i in picked) picked - i else picked + i
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (picked.isNotEmpty()) {
            SfButton(
                text = if (importing) "写入中…" else "🐧 导入 QQ（${picked.size} 个）",
                enabled = !importing && qqUri != null,
                container = com.stickerferry.ui.theme.QqBlue,
                onClick = {
                    val dir = qqUri ?: return@SfButton
                    importing = true
                    scope.launch {
                        val selected = picked.map { bitmaps[it] }
                        val r = QQImporter.importBitmaps(ctx, dir, selected)
                        importing = false
                        android.widget.Toast.makeText(
                            ctx,
                            "已写入 ${r.okCount} 个，跳过 ${r.skippedCount} 个",
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                    }
                },
            )
            if (qqUri == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "⚠️ 还没授权 QQ 表情目录，请先去表情库授权",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun ResultCell(
    modifier: Modifier,
    bitmap: Bitmap,
    picked: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                if (picked) 2.dp else 1.dp,
                if (picked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(16.dp),
            )
            .clickable { onClick() },
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
        if (picked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}
