package io.oniimai.kanade

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.*

object OniTokens {
    val space = 8.dp
    val gap = 12.dp
    val inset = 16.dp
    val section = 24.dp
    val width = 840.dp
    val target = 48.dp
    val compactRadius = 12.dp
    val artworkRadius = 8.dp
    val shortcutWidth = 116.dp
    val shortcutHeight = 36.dp
    val title = 22.sp
    val body = 17.sp
    val label = 15.sp
    val rowLabel = 14.sp
    val caption = 13.sp
    val small = 12.sp
    val sensorLabel = 10.sp
    val metric = 27.sp
    val metricSmall = 20.sp
    val metricMinimum = 16.sp
    val displayMetric = 32.sp
    const val motion = 180
}
internal fun tr(ko: String, zh: String) = GameUi.tr(ko, zh)

@Composable
internal fun OniTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val family = remember { FontFamily(GameAssets.regular(context)) }
    val base = remember { defaultTextStyles() }
    val styles = remember(family) {
        fun TextStyle.native() = copy(fontFamily = family)
        defaultTextStyles(base.main.native(), base.paragraph.native(), base.body1.native(),
            base.body2.native(), base.button.native(), base.footnote1.native(), base.footnote2.native(),
            base.headline1.native(), base.headline2.native(), base.subtitle.native(),
            base.title1.native(), base.title2.native(), base.title3.native(), base.title4.native())
    }
    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), textStyles = styles, content = content)
}

@Composable
internal fun Action(label: String, primary: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text = label, onClick = onClick, enabled = enabled, modifier = modifier,
        minHeight = OniTokens.target,
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}

@Composable
internal fun ShortcutAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(OniTokens.shortcutHeight * scale),
            minHeight = OniTokens.shortcutHeight, cornerRadius = OniTokens.compactRadius,
            insideMargin = PaddingValues(horizontal = OniTokens.gap, vertical = OniTokens.space / 2)) {
            Text(label, fontSize = OniTokens.caption, maxLines = 1, color = MiuixTheme.colorScheme.onSecondaryVariant)
        }
    }
}

@Composable
internal fun PageHeader(title: String, back: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = OniTokens.inset, vertical = OniTokens.space),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        if (back != null) Action(tr("뒤로", "返回"), onClick = back)
        Text(title, modifier = Modifier.weight(1f), fontSize = OniTokens.title, fontWeight = FontWeight.SemiBold)
        action?.invoke()
    }
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, fontSize = OniTokens.caption, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}

@Composable
internal fun SectionTitle(text: String) {
    Text(text, Modifier.padding(start = OniTokens.inset, top = OniTokens.space, bottom = OniTokens.space),
        fontSize = OniTokens.rowLabel, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
}
