package com.stickerferry.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerferry.data.AccountScanner
import com.stickerferry.data.Binding
import com.stickerferry.data.BindingHealth
import com.stickerferry.data.BindingStatus
import com.stickerferry.ui.theme.OkGreen
import com.stickerferry.ui.theme.QqBlue
import com.stickerferry.ui.theme.WxGreen
import com.stickerferry.util.SafHelper
import com.stickerferry.util.StorageAccess

/**
 * 授权与绑定中心。
 *
 * 这一页要回答三个问题，顺序也按重要性排：
 *   1. 现在拿到哪些权限了？（系统权限 / 目录授权）
 *   2. 绑了哪些账号，它们现在健康吗？（绑定状态）
 *   3. 为什么 QQ / 微信 没法直接授权？（可行性说明，避免用户反复猜）
 */
@Composable
fun BindingScreen(vm: MainViewModel, onDiagnose: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val bindings by vm.bindings.collectAsState()
    val health by vm.health.collectAsState()
    val storageOk by vm.storageOk.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val discovery by vm.discovery.collectAsState()

    var renameTarget by remember { mutableStateOf<Binding?>(null) }
    var renameText by remember { mutableStateOf("") }
    var unbindTarget by remember { mutableStateOf<Binding?>(null) }
    var showAuthInfo by remember { mutableStateOf(false) }

    val qqPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            SafHelper.persistPermission(ctx, it)
            vm.addSafBinding(AccountScanner.Platform.QQ, it)
        }
    }
    val wxPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            SafHelper.persistPermission(ctx, it)
            vm.addSafBinding(AccountScanner.Platform.WECHAT, it)
        }
    }

    val safCount = bindings.count { it.sourceKind == "SAF" }
    val broken = bindings.count { needsRepair(it.id, health) }

    Column(Modifier.fillMaxSize()) {
        // ---- 顶栏 ----
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
                Text("授权与绑定", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "权限、目录、账号状态",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (scanning) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            /* ---------- 1. 授权总览 ---------- */
            SfCard(Modifier.padding(bottom = 6.dp)) {
                Column(Modifier.padding(15.dp)) {
                    Text("当前授权状态", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(11.dp))

                    AuthLine(
                        title = "所有文件访问权限",
                        detail = if (storageOk) "已授予，可直接遍历 QQ / 微信 的数据目录"
                        else "未授予，Android 11+ 下读不到 Android/data",
                        ok = storageOk,
                    )
                    Spacer(Modifier.height(9.dp))
                    AuthLine(
                        title = "目录授权（SAF）",
                        detail = if (safCount > 0) "$safCount 个目录已持久化授权"
                        else "未使用，可作为权限被拦截时的兜底",
                        ok = safCount > 0,
                        neutralWhenNotOk = true,
                    )
                    Spacer(Modifier.height(9.dp))
                    AuthLine(
                        title = "账号绑定",
                        detail = when {
                            bindings.isEmpty() -> "还没有绑定任何来源"
                            broken > 0 -> "${bindings.size} 个绑定，其中 $broken 个需要修复"
                            else -> "${bindings.size} 个绑定全部正常"
                        },
                        ok = bindings.isNotEmpty() && broken == 0,
                        neutralWhenNotOk = bindings.isEmpty(),
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Box(Modifier.weight(1f)) {
                            SfButton(
                                text = if (scanning) "扫描中…" else "重新扫描",
                                enabled = !scanning,
                                onClick = { vm.autoScan() },
                            )
                        }
                        if (!storageOk) {
                            Box(Modifier.weight(1f)) {
                                SfButton(
                                    text = "去开启权限",
                                    container = WarnColor,
                                    onClick = { StorageAccess.openAllFilesSettings(ctx) },
                                )
                            }
                        }
                    }
                }
            }

            // 扫描概况
            discovery?.let { d ->
                Spacer(Modifier.height(9.dp))
                Text(
                    "上次扫描：命中 ${d.hits.size} 处 · 识别 ${d.imageTotal} 张 · " +
                        "遍历 ${d.visitedDirs} 个目录 · ${d.elapsedMs} ms",
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            /* ---------- 2. 已绑定账号 ---------- */
            Spacer(Modifier.height(16.dp))
            SectionTitle("已绑定账号", "${bindings.size} 个")

            if (bindings.isEmpty()) {
                SfNotice(
                    NoticeKind.WARN, "🔗",
                    "还没有绑定任何来源。先开「所有文件访问权限」并扫描一次；" +
                        "如果扫描不到，用下面的目录授权手动指一个目录。",
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    bindings.forEach { b ->
                        BindingCard(
                            binding = b,
                            health = health[b.id],
                            onRename = {
                                renameTarget = b
                                renameText = b.alias
                            },
                            onUnbind = { unbindTarget = b },
                        )
                    }
                }
            }

            /* ---------- 3. 目录授权 ---------- */
            Spacer(Modifier.height(18.dp))
            SectionTitle("目录授权（兜底）")
            Text(
                "自动扫描不到时，手动指定一个目录。授权会被系统记住，重启 App 依然有效。",
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(9.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                Box(Modifier.weight(1f)) {
                    SfButton(
                        text = "🐧 选 QQ 目录",
                        container = QqBlue,
                        onClick = { qqPicker.launch(null) },
                    )
                }
                Box(Modifier.weight(1f)) {
                    SfButton(
                        text = "💬 选微信目录",
                        container = WxGreen,
                        onClick = { wxPicker.launch(null) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            SfNotice(
                NoticeKind.INFO, "ℹ️",
                "注意：Android 11 起系统选择器不允许进入 Android/data 目录，" +
                    "会提示「无法使用此文件夹」。这是系统限制，任何 App 都绕不过。" +
                    "所以这条路主要给老系统、以及你把表情存到自建目录时用。",
            )

            /* ---------- 4. QQ / 微信 能不能授权 ---------- */
            Spacer(Modifier.height(18.dp))
            QQWxAuthCard(expanded = showAuthInfo, onToggle = { showAuthInfo = !showAuthInfo })

            /* ---------- 5. 诊断 ---------- */
            Spacer(Modifier.height(16.dp))
            SfButton(
                text = "🔍 跑一次诊断，生成报告",
                container = Color(0xFF5B6CFF),
                onClick = onDiagnose,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "诊断会把每条候选路径的实际状态列出来，是排查「为什么扫不到」最有效的一步。",
                fontSize = 10.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(120.dp))
        }
    }

    /* ---------- 改名弹层 ---------- */
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("给这个来源起个名字", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "拿不到真实 QQ 号 / 微信号（腾讯没开放），所以名字由你自己定。",
                        fontSize = 11.5.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        label = { Text("名字", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameBinding(target.id, renameText)
                    renameTarget = null
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("取消") }
            },
        )
    }

    /* ---------- 解绑确认 ---------- */
    unbindTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { unbindTarget = null },
            title = { Text("解绑「${target.alias}」？", fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "只删除这条绑定记录，不会动手机上的任何文件。下次扫描如果还能发现，会重新出现。",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.unbind(target.id)
                    unbindTarget = null
                }) { Text("解绑", color = ErrColor) }
            },
            dismissButton = {
                TextButton(onClick = { unbindTarget = null }) { Text("取消") }
            },
        )
    }
}

/* ==================== 小组件 ==================== */

private fun needsRepair(id: String, health: Map<String, BindingHealth>): Boolean {
    val h = health[id] ?: return false
    return h.status == BindingStatus.PATH_MISSING || h.status == BindingStatus.DENIED
}

@Composable
private fun AuthLine(
    title: String,
    detail: String,
    ok: Boolean,
    neutralWhenNotOk: Boolean = false,
) {
    val dot = when {
        ok -> OkGreen
        neutralWhenNotOk -> Color(0xFF8C99B3)
        else -> ErrColor
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .clip(RoundedCornerShape(50))
                .background(dot)
        )
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (ok || neutralWhenNotOk) MaterialTheme.colorScheme.onSurface else ErrColor,
            )
            Text(
                detail,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BindingCard(
    binding: Binding,
    health: BindingHealth?,
    onRename: () -> Unit,
    onUnbind: () -> Unit,
) {
    val status = health?.status ?: BindingStatus.PENDING
    val accent = when (status) {
        BindingStatus.ACTIVE -> OkGreen
        BindingStatus.EMPTY -> Color(0xFF8C99B3)
        BindingStatus.DENIED -> ErrColor
        BindingStatus.PATH_MISSING -> ErrColor
        BindingStatus.PENDING -> WarnColor
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
            .padding(13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (binding.platformEnum == AccountScanner.Platform.QQ) "🐧" else "💬",
                fontSize = 17.sp,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(binding.alias, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    binding.shortPath,
                    fontSize = 9.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            SfTag(status.label, accent)
        }

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${health?.count ?: binding.lastCount} 个表情",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (binding.platformEnum == AccountScanner.Platform.QQ) QqBlue else WxGreen,
            )
            Text(
                "  ·  ${binding.via}",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "改名",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onRename() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Text(
                "解绑",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ErrColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onUnbind() }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }

        // 状态解释：出问题时给具体原因，而不是只给一个红点
        if (status != BindingStatus.ACTIVE && status != BindingStatus.EMPTY) {
            Spacer(Modifier.height(7.dp))
            Text(
                "⚠️ ${health?.detail ?: "状态未知"}",
                fontSize = 10.sp,
                lineHeight = 15.sp,
                color = accent,
            )
        }
    }
}

/**
 * 「能不能通过 QQ / 微信 授权来解决」—— 这一块是刻意做成常驻的说明，
 * 而不是藏在帮助文档里。
 *
 * 因为这是用户最自然会想到的方向，也是实测走不通的方向。
 * 与其让用户反复尝试、反复失望，不如把结论和证据直接摆在功能入口旁边。
 */
@Composable
private fun QQWxAuthCard(expanded: Boolean, onToggle: () -> Unit) {
    SfCard {
        Column(Modifier.padding(15.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("🔐", fontSize = 18.sp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("为什么不能让 QQ / 微信 授权？", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "这两家都没有「读本地表情」的接口",
                        fontSize = 10.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    if (expanded) "收起" else "展开",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            if (expanded) {
                Spacer(Modifier.height(13.dp))

                Text(
                    "QQ 互联（connect.qq.com）开放的能力只有：登录、拿昵称头像、分享、" +
                        "支付、小程序。微信开放平台同理：登录、分享、支付、公众号、小程序。" +
                        "两边都没有任何读取用户本地表情包的接口。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "更根本的原因是：收藏的表情本来就存在这台手机上（文件 + 加密数据库），" +
                        "不在账号的云端体系里。所以「授权」这个动作，在架构上就没有对应的东西可授权。" +
                        "微信的表情开放平台是给设计师投稿上架用的，方向正好相反。",
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(12.dp))
                Text("能真正解决「读不到」的三条路：", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))

                AuthPath(
                    "1", "所有文件访问权限", OkGreen,
                    "Android 官方给系统工具类应用的口子，拿到后可直读 Android/data。" +
                        "本项目优先走这条。",
                )
                Spacer(Modifier.height(7.dp))
                AuthPath(
                    "2", "目录授权（SAF）", WarnColor,
                    "让系统记住某一个目录的访问权。老系统可用；Android 11+ 进不去 Android/data，属系统限制。",
                )
                Spacer(Modifier.height(7.dp))
                AuthPath(
                    "3", "手动导入", Color(0xFF8C99B3),
                    "前两条都不通时的终点。从相册或文件里选图，永远可用，不依赖任何权限。",
                )

                Spacer(Modifier.height(12.dp))
                SfNotice(
                    NoticeKind.WARN, "🧰",
                    "还有一条路，专治「ROM 把 Android/data 彻底拦死」：用手机自带的文件管理器" +
                        "（多数国产 ROM 的文件管理器能进 Android/data）把 emoji 目录整个复制到" +
                        "手机存储根目录，比如 /手机存储/StickerFerry/，然后用上面的「目录授权」指过去。" +
                        "笨，但能用，而且不依赖任何特殊权限。",
                )
            }
        }
    }
}

@Composable
private fun AuthPath(no: String, title: String, color: Color, desc: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .size(17.dp)
                .clip(RoundedCornerShape(50))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(no, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                fontSize = 10.5.sp,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
