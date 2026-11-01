package com.stickerferry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.stickerferry.util.SecureStore

/**
 * AI 配置页。
 *
 * 这是整个 App 里唯一需要用户"出门办点事"的环节，
 * 所以引导要把话说透：为什么需要、大概多少钱、怎么弄。
 */
@Composable
fun AiConfigScreen(vm: MainViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current

    var provider by remember { mutableStateOf(SecureStore.getProvider(ctx)) }
    var keyInput by remember { mutableStateOf("") }
    var configured by remember { mutableStateOf(SecureStore.isConfigured(ctx)) }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
    ) {
        Column(Modifier.padding(top = 8.dp, bottom = 14.dp)) {
            Text("AI 配置", fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            Text(
                "接入你自己的模型服务",
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- 状态卡 ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (configured) Color(0xFFECFDF3) else Color(0xFFFFF8EC)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (configured) "✅" else "⚠️", fontSize = 24.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        if (configured) "已配置，可以生成表情了" else "还没配置模型",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (configured) Color(0xFF146C3E) else Color(0xFF8A5A00),
                    )
                    Text(
                        if (configured) {
                            if (provider == SecureStore.Provider.DOMESTIC) "国内模型 · Key 已保存在本机"
                            else "国际模型 · Key 已保存在本机"
                        } else "AI 生成功能需要你自己的模型服务，配置后即可使用",
                        fontSize = 11.sp,
                        color = if (configured) Color(0xFF2F7D51) else Color(0xFFA8761F),
                        lineHeight = 16.sp,
                    )
                }
            }

            Spacer(Modifier.height(18.dp))

            // ---- 服务商选择 ----
            SectionTitle("选择模型服务商")
            ProviderOption(
                emoji = "🇨🇳",
                title = "国内模型服务",
                desc = "支付宝/微信充值 · 无需科学上网 · 中文提示词友好",
                selected = provider == SecureStore.Provider.DOMESTIC,
            ) { provider = SecureStore.Provider.DOMESTIC }

            Spacer(Modifier.height(9.dp))

            ProviderOption(
                emoji = "🌍",
                title = "国际模型服务",
                desc = "需信用卡 · 需要网络条件 · 部分风格更细腻",
                selected = provider == SecureStore.Provider.OVERSEAS,
            ) { provider = SecureStore.Provider.OVERSEAS }

            Spacer(Modifier.height(18.dp))

            // ---- Key 输入 ----
            SectionTitle("填入 API Key")
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("sk-xxxxxxxxxxxxxxxxxxxx", fontSize = 12.sp) },
                shape = RoundedCornerShape(13.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "🔒 Key 只保存在你这台手机上，不会上传任何服务器。",
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (configured) {
                Spacer(Modifier.height(10.dp))
                SfNotice(
                    NoticeKind.OK,
                    "🔑",
                    "当前已保存：${SecureStore.masked(SecureStore.getKey(ctx))}",
                )
            }

            Spacer(Modifier.height(18.dp))

            // ---- 注册引导 ----
            SectionTitle("怎么拿到 Key")
            SfCard {
                Column(Modifier.padding(16.dp)) {
                    GuideStep(1, "去模型服务商官网注册账号，完成实名认证")
                    GuideStep(2, "在控制台充值少量金额（一般 10 元就够做几百张表情）")
                    GuideStep(3, "创建一个 API Key，复制那一长串字符")
                    GuideStep(4, "粘贴到上面的输入框，点下面的「保存」")
                }
            }

            Spacer(Modifier.height(14.dp))

            // ---- 费用说明 ----
            SfNotice(
                NoticeKind.WARN,
                "💰",
                "按用量计费，通常每张图几毛钱。做一套 20 张的表情包大约几块钱。" +
                    "具体价格以服务商官网为准，本 App 不参与任何计费。",
            )

            Spacer(Modifier.height(18.dp))

            SfButton(
                text = "💾 保存配置",
                onClick = {
                    if (keyInput.isBlank()) {
                        android.widget.Toast.makeText(ctx, "请先填入 API Key", android.widget.Toast.LENGTH_SHORT).show()
                        return@SfButton
                    }
                    SecureStore.saveKey(ctx, keyInput)
                    SecureStore.saveProvider(ctx, provider)
                    configured = true
                    vm.refreshAiConfig()
                    android.widget.Toast.makeText(ctx, "✅ 配置已保存", android.widget.Toast.LENGTH_SHORT).show()
                    onDone()
                },
            )

            if (configured) {
                Spacer(Modifier.height(9.dp))
                OutlinedButton(
                    onClick = {
                        SecureStore.clear(ctx)
                        configured = false
                        keyInput = ""
                        vm.refreshAiConfig()
                        android.widget.Toast.makeText(ctx, "已清除 Key", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(15.dp),
                ) {
                    Text("🗑️ 清除 Key", fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun ProviderOption(
    emoji: String,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
            .border(
                if (selected) 1.6.dp else 1.dp,
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(15.dp),
            )
            .clickable { onClick() }
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Text(emoji, fontSize = 21.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
            Text(
                desc,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }
        Box(
            Modifier
                .size(19.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant,
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@Composable
private fun GuideStep(n: Int, text: String) {
    Row(
        Modifier.padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "$n",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text,
            fontSize = 11.5.sp,
            lineHeight = 17.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
