package com.stickerferry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import com.stickerferry.data.Source
import com.stickerferry.data.Sticker
import com.stickerferry.util.SafHelper
import com.stickerferry.ui.theme.QqBlue
import com.stickerferry.ui.theme.WxGreen

@Composable
fun LibraryScreen(vm: MainViewModel, onGoStudio: () -> Unit) {
    val ctx = LocalContext.current
    val scanning by vm.scanning.collectAsState()
    val report by vm.report.collectAsState()
    val filter by vm.filter.collectAsState()
    val selected by vm.selected.collectAsState()
    val qqUri by vm.qqUri.collectAsState()
    val wechatUri by vm.wechatUri.collectAsState()

    // SAF 目录选择器
    val qqPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            SafHelper.persistPermission(ctx, it)
            vm.onQqPicked(it)
        }
    }
    val wechatPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            SafHelper.persistPermission(ctx, it)
            vm.onWechatPicked(it)
        }
    }

    val visible = vm.visible()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
        ) {
            // ---- 顶部栏 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("表情库", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "共 ${report.total} 个表情",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { vm.scan() }) {
                    Text("🔄", fontSize = 17.sp)
                }
            }

            // ---- 来源统计卡 ----
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SourceStatCard(
                    modifier = Modifier.weight(1f),
                    emoji = "🐧",
                    label = "QQ 表情",
                    count = report.qq.size,
                    accent = QqBlue,
                    onClick = {
                        if (qqUri == null) qqPicker.launch(null) else vm.setFilter(Filter.QQ)
                    },
                )
                SourceStatCard(
                    modifier = Modifier.weight(1f),
                    emoji = "💬",
                    label = "微信表情",
                    count = report.wechat.size,
                    accent = WxGreen,
                    onClick = {
                        if (wechatUri == null) wechatPicker.launch(null) else vm.setFilter(Filter.WECHAT)
                    },
                )
            }

            Spacer(Modifier.height(14.dp))

            // ---- 授权引导（未授权时显示）----
            if (qqUri == null || wechatUri == null) {
                SfNotice(
                    kind = NoticeKind.INFO,
                    icon = "🔓",
                    text = "Android 11+ 需手动授权目录才能读取。点上方统计卡选择对应目录；" +
                        "未授权的来源会显示为 0。",
                )
                Spacer(Modifier.height(12.dp))
            }

            // ---- 部分失败提示 ----
            report.qqError?.let {
                SfNotice(NoticeKind.WARN, "⚠️", "QQ：$it")
                Spacer(Modifier.height(8.dp))
            }
            report.wechatError?.let {
                SfNotice(NoticeKind.WARN, "⚠️", "微信：$it")
                Spacer(Modifier.height(8.dp))
            }

            // ---- 筛选 ----
            SfSegmented(
                options = listOf(
                    Filter.ALL to "全部",
                    Filter.QQ to "仅 QQ",
                    Filter.WECHAT to "仅微信",
                    Filter.ANIM to "动图",
                ),
                selected = filter,
                onSelect = { vm.setFilter(it) },
            )

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = { vm.selectAll() }) { Text("全选", fontSize = 12.sp) }
                Text(
                    "${visible.size} 个",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
                TextButton(onClick = { vm.clearSelection() }) { Text("取消", fontSize = 12.sp) }
            }

            // ---- 网格 / 空状态 / 扫描中 ----
            when {
                scanning -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(14.dp))
                            Text("正在扫描两个来源…", fontSize = 12.sp)
                        }
                    }
                }

                visible.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🗂️", fontSize = 42.sp)
                            Spacer(Modifier.height(12.dp))
                            Text("这里还空空的", fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "点上方统计卡授权目录\n或去 AI 工坊造几个新表情",
                                fontSize = 11.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 18.sp,
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.width(180.dp)) {
                                SfButton("✨ 去 AI 工坊", onGoStudio, container = Color(0xFFF5A524))
                            }
                        }
                    }
                }

                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                        contentPadding = PaddingValues(bottom = 120.dp),
                    ) {
                        items(visible, key = { it.id }) { s ->
                            StickerCell(
                                sticker = s,
                                picked = s.id in selected,
                                onClick = { vm.toggleSelect(s.id) },
                            )
                        }
                    }
                }
            }
        }

        // ---- 底部操作条（有选中时升起）----
        if (selected.isNotEmpty()) {
            PlayerBar(
                count = selected.size,
                onClear = { vm.clearSelection() },
                onTransfer = {
                    vm.addSelectionToQueue()
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(14.dp),
            )
        }
    }
}

@Composable
private fun SourceStatCard(
    modifier: Modifier,
    emoji: String,
    label: String,
    count: Int,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
    ) {
        Text("$emoji $label", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(7.dp))
        Text(
            "$count",
            fontSize = 25.sp,
            fontWeight = FontWeight.ExtraBold,
            color = accent,
        )
    }
}

@Composable
private fun StickerCell(sticker: com.stickerferry.data.Sticker, picked: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(if (picked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
            .border(
                width = if (picked) 1.6.dp else 1.dp,
                color = if (picked) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() },
    ) {
        AsyncImage(
            model = sticker.uri,
            contentDescription = sticker.name,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp),
        )

        // 来源角标
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(
                    when (sticker.source) {
                        Source.QQ -> QqBlue
                        Source.WECHAT -> WxGreen
                        Source.AI_GEN -> Color(0xFF5B6CFF)
                        Source.STOCK -> Color(0xFFF5A524)
                        Source.ALBUM -> Color(0xFF8C99B3)
                    }
                )
                .padding(horizontal = 5.dp, vertical = 1.5.dp),
        ) {
            Text(
                sticker.sourceLabel,
                fontSize = 8.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
            )
        }

        if (picked) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(17.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("✓", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun PlayerBar(
    count: Int,
    onClear: () -> Unit,
    onTransfer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xF21A2233))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("已选 $count 个", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Text("可批量投放", fontSize = 10.sp, color = Color(0x99FFFFFF))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onClear) {
                Text("取消", color = Color.White, fontSize = 12.sp)
            }
            Button(
                onClick = onTransfer,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Text("投放 →", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
