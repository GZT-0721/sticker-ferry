package com.stickerferry.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerferry.util.SecureStore

@Composable
fun SettingsScreen(
    vm: MainViewModel,
    onGoAiConfig: () -> Unit,
    onGoBindings: () -> Unit,
    onGoDiagnose: () -> Unit,
) {
    val ctx = LocalContext.current
    val qqUri by vm.qqUri.collectAsState()
    val wechatUri by vm.wechatUri.collectAsState()
    val aiConfigured by vm.aiConfigured.collectAsState()
    val storageOk by vm.storageOk.collectAsState()
    val bindings by vm.bindings.collectAsState()
    val health by vm.health.collectAsState()

    val brokenCount = bindings.count {
        val h = health[it.id]
        h == null || h.status == com.stickerferry.data.BindingStatus.DENIED ||
            h.status == com.stickerferry.data.BindingStatus.PATH_MISSING
    }

    var allowWebSearch by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 14.dp)) {
            Text("设置", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "权限、路径、AI 能力",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionTitle("数据来源与授权")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SfSettingItem(
                    "🔐",
                    "授权与账号绑定",
                    when {
                        !storageOk -> "缺少所有文件访问权限 · 点此处理"
                        brokenCount > 0 -> "$brokenCount 个绑定需要修复 · 点此处理"
                        bindings.isEmpty() -> "还没有绑定来源 · 点此扫描或手动授权"
                        else -> "${bindings.size} 个绑定，全部正常"
                    },
                    onClick = onGoBindings,
                )
                SfSettingItem(
                    "🔍",
                    "诊断报告",
                    "扫不到表情时先跑这个，能定位卡在哪一层",
                    onClick = onGoDiagnose,
                )
                SfSettingItem(
                    "🐧",
                    "QQ 目录授权（手动）",
                    if (qqUri != null) "已授权一个目录" else "未使用 · 在授权页里选目录",
                    onClick = onGoBindings,
                )
                SfSettingItem(
                    "💬",
                    "微信目录授权（手动）",
                    if (wechatUri != null) "已授权一个目录" else "未使用 · 在授权页里选目录",
                    onClick = onGoBindings,
                )
            }

            Spacer(Modifier.height(18.dp))

            SectionTitle("AI 能力")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SfSettingItem(
                    "🔑",
                    "AI 模型配置",
                    if (aiConfigured) "已配置 · ${if (SecureStore.getProvider(ctx) == SecureStore.Provider.DOMESTIC) "国内模型" else "国际模型"}"
                    else "未配置 · 点此填入 API Key",
                    onClick = onGoAiConfig,
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("允许全网搜索", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "关闭后仅在可商用白名单图库中检索",
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = allowWebSearch,
                        onCheckedChange = { allowWebSearch = it },
                    )
                }

                if (allowWebSearch) {
                    SfNotice(
                        NoticeKind.WARN,
                        "⚖️",
                        "已开启全网搜索。结果会标注来源域名，但许可大多未知 —— 自用可以，转发前建议先确认来源。",
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            SectionTitle("关于")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SfSettingItem("⛵", "表情摆渡 v1.0", "自用工具 · 仅供已登录设备本地使用")
                SfSettingItem("🔒", "隐私说明", "纯本地运行，不采集不上传") {
                    android.widget.Toast.makeText(
                        ctx,
                        "只在本机读取已授权目录中的表情，全部处理都在设备上完成。仅 AI 功能联网。",
                        android.widget.Toast.LENGTH_LONG,
                    ).show()
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}
