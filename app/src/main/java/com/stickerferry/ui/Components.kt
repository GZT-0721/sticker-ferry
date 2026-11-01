package com.stickerferry.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stickerferry.ui.theme.DangerRed
import com.stickerferry.ui.theme.OkGreen
import com.stickerferry.ui.theme.WarnAmber

/** 统一的卡片样式，整个 App 复用 */
@Composable
fun SfCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))
        .background(MaterialTheme.colorScheme.surface)
        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))

    Column(
        modifier = if (onClick != null) base.clickable { onClick() } else base,
        content = content,
    )
}

/** 提示条 —— 对应原型里的 notice / awareness 组件 */
enum class NoticeKind { INFO, WARN, OK, ERROR }

@Composable
fun SfNotice(
    kind: NoticeKind,
    icon: String,
    text: String,
    modifier: Modifier = Modifier,
) {
    val (bg, fg) = when (kind) {
        NoticeKind.INFO -> Color(0xFFEEF4FF) to Color(0xFF29508F)
        NoticeKind.WARN -> Color(0xFFFFF8EC) to Color(0xFF8A5A00)
        NoticeKind.OK -> Color(0xFFECFDF3) to Color(0xFF146C3E)
        NoticeKind.ERROR -> Color(0xFFFDECEC) to Color(0xFFA32020)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(bg)
            .padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(icon, fontSize = 14.sp)
        Text(
            text = text,
            fontSize = 11.5.sp,
            lineHeight = 18.sp,
            color = fg,
        )
    }
}

/** 章节小标题 */
@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        if (trailing != null) {
            Text(trailing, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 主按钮 */
@Composable
fun SfButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = Color.White,
    enabled: Boolean = true,
    icon: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(15.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = contentColor,
        ),
    ) {
        if (icon != null) {
            Text(icon, fontSize = 15.sp)
            Spacer(Modifier.width(7.dp))
        }
        Text(text, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    }
}

/** 分段控件 —— 对应原型里的 chip / seg */
@Composable
fun <T> SfSegmented(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (on) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (on) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 设置项 */
@Composable
fun SfSettingItem(
    icon: String,
    title: String,
    subtitle: String,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(15.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(icon, fontSize = 16.sp)
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(
                subtitle,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 15.sp,
            )
        }
        if (trailing != null) trailing()
        else Text("›", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 语义色小标签 */
@Composable
fun SfTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(text, fontSize = 9.5.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

val OkColor = OkGreen
val WarnColor = WarnAmber
val ErrColor = DangerRed
