// Modified 2026-09-29 (UI refinement pass, see CHANGES-UI.md). Original: KanadeDX-Oniimai 1.1.0-rc5 @ 1c9c518b.
package io.oniimai.kanade

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.basic.Check
import top.yukonga.miuix.kmp.icon.icons.useful.Back
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.theme.*
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

object OniTokens {
    val space = 8.dp
    val gap = 12.dp
    val inset = 16.dp
    val section = 24.dp
    val width = 840.dp
    val target = 48.dp
    val compactRadius = 12.dp
    val artworkRadius = 8.dp
    /** Matches Miuix CardDefaults.CornerRadius so Android-View overlays line up with Compose cards. */
    val cardRadius = 16.dp
    val shortcutWidth = 116.dp
    val shortcutHeight = 36.dp
    val headerAction = 36.dp
    /** Large-title and section-title start edge: card margin (16) + row inset (16). */
    val titleInset = 32.dp
    val stepHeight = 4.dp
    val dot = 6.dp
    val rowIcon = 34.dp
    val rowIconRadius = 10.dp
    val heroRadius = 24.dp
    val heroMark = 60.dp
    val pillRadius = 8.dp
    val barHeight = 6.dp
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
    val clock = 48.sp
    const val motion = 180
}
internal fun tr(ko: String, zh: String) = GameUi.tr(ko, zh)
/** Validation/error text; the same red the Android-View helpers use. */
internal val ERROR_TEXT = androidx.compose.ui.graphics.Color(GameUi.ERROR)

/** [family] is the system MiSans family on device; null keeps the platform default (desktop preview). */
@Composable
internal fun OniTheme(family: FontFamily? = null, content: @Composable () -> Unit) {
    val base = remember { defaultTextStyles() }
    val styles = remember(family) {
        fun TextStyle.native() = if (family == null) this else copy(fontFamily = family)
        defaultTextStyles(base.main.native(), base.paragraph.native(), base.body1.native(),
            base.body2.native(), base.button.native(), base.footnote1.native(), base.footnote2.native(),
            base.headline1.native(), base.headline2.native(), base.subtitle.native(),
            base.title1.native(), base.title2.native(), base.title3.native(), base.title4.native())
    }
    MiuixTheme(colors = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), textStyles = styles, content = content)
}

/** MIUI list scrolling: spring bounce at the edges and a light tick when a fling hits the end. */
internal fun Modifier.miuiScroll(): Modifier = this.scrollEndHaptic().overScrollVertical()

@Composable
internal fun Action(label: String, primary: Boolean = false, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    TextButton(text = label, onClick = onClick, enabled = enabled, modifier = modifier,
        minHeight = OniTokens.target,
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}

@Composable
internal fun ShortcutAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(OniTokens.shortcutHeight * scale),
            minHeight = OniTokens.shortcutHeight, cornerRadius = OniTokens.compactRadius,
            insideMargin = PaddingValues(horizontal = OniTokens.gap, vertical = OniTokens.space / 2)) {
            Text(label, fontSize = OniTokens.caption, maxLines = 1, color = MiuixTheme.colorScheme.onSecondaryVariant)
        }
    }
}

/** System-style back arrow; replaces the former text button so the title owns the header. */
@Composable
internal fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.padding(start = OniTokens.gap).semantics { contentDescription = tr("뒤로", "返回") }) {
        Icon(MiuixIcons.Useful.Back, contentDescription = null, tint = MiuixTheme.colorScheme.onBackground)
    }
}

/** Small pill action for a page header: tinted when it is the page's main action, quiet otherwise. */
@Composable
internal fun HeaderAction(label: String, primary: Boolean, onClick: () -> Unit) {
    TextButton(text = label, onClick = onClick, minHeight = OniTokens.headerAction, cornerRadius = OniTokens.headerAction / 2,
        insideMargin = PaddingValues(horizontal = OniTokens.inset, vertical = 0.dp),
        colors = if (primary) ButtonDefaults.textButtonColorsPrimary()
            else ButtonDefaults.textButtonColors(color = MiuixTheme.colorScheme.tertiaryContainer, textColor = MiuixTheme.colorScheme.onTertiaryContainer))
}

/** Compact, non-collapsing header for Android-View hosts such as the dashboard board. */
@Composable
internal fun PageHeader(title: String, back: (() -> Unit)? = null, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .padding(start = if (back != null) 0.dp else OniTokens.inset, end = OniTokens.inset, top = OniTokens.space / 2, bottom = OniTokens.space / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        if (back != null) BackButton(back)
        Text(title, modifier = Modifier.weight(1f), fontSize = OniTokens.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
        action?.invoke()
    }
}

@Composable
internal fun Caption(text: String, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MiuixTheme.colorScheme.onSurfaceVariantSummary) {
    Text(text, modifier, fontSize = OniTokens.caption, color = color)
}

/** MIUI group label: muted blue-grey, aligned with the row text inside the card below it. */
@Composable
internal fun SectionTitle(text: String) {
    SmallTitle(text, insideMargin = PaddingValues(start = OniTokens.inset, end = OniTokens.inset, top = OniTokens.space, bottom = OniTokens.space))
}

/**
 * Single-choice row. MIUI marks the current value with a check on the right; an arrow would
 * wrongly suggest that tapping opens another page.
 */
@Composable
internal fun OptionRow(title: String, selected: Boolean, summary: String? = null, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    BasicComponent(
        title = title,
        titleColor = BasicComponentDefaults.titleColor(color = if (selected) colors.primary else colors.onSurface),
        summary = summary,
        rightActions = {
            if (selected) Icon(MiuixIcons.Basic.Check, contentDescription = null, tint = if (enabled) colors.primary else colors.disabledOnSurface,
                modifier = Modifier.size(20.dp))
        },
        modifier = Modifier.semantics { this.selected = selected },
        onClick = onClick,
        enabled = enabled
    )
}

/** Segmented progress for multi-step flows (first-run setup). */
@Composable
internal fun StepIndicator(step: Int, count: Int, modifier: Modifier = Modifier) {
    val colors = MiuixTheme.colorScheme
    Row(modifier.fillMaxWidth().semantics { contentDescription = "${step + 1} / $count" },
        horizontalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
        repeat(count) { index ->
            Box(Modifier.weight(1f).height(OniTokens.stepHeight).clip(RoundedCornerShape(OniTokens.stepHeight))
                .background(if (index <= step) colors.primary else colors.dividerLine))
        }
    }
}

/** HyperOS-style tile colours behind white row glyphs; same in day and night like the system Settings app. */
internal object IconTint {
    val blue = Color(0xFF3482FF)
    val green = Color(0xFF22B573)
    val orange = Color(0xFFFF8A1F)
    val purple = Color(0xFF8C6CFF)
    val pink = Color(0xFFF2528C)
    val slate = Color(0xFF6E7A8A)
}

/** Rounded-square leading icon for a settings row. Pass either a vector glyph or a one/two-character label. */
@Composable
internal fun RowIcon(tint: Color, icon: ImageVector? = null, label: String? = null) {
    Box(Modifier.padding(end = OniTokens.inset).size(OniTokens.rowIcon).clip(RoundedCornerShape(OniTokens.rowIconRadius)).background(tint),
        contentAlignment = Alignment.Center) {
        if (icon != null) Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        else if (label != null) Text(label, color = Color.White, fontSize = OniTokens.rowLabel, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Small tinted badge, e.g. version, level or rank. */
@Composable
internal fun Pill(text: String, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.onTertiaryContainer,
                  background: Color = MiuixTheme.colorScheme.tertiaryContainer) {
    Text(text, modifier.clip(RoundedCornerShape(OniTokens.pillRadius)).background(background).padding(horizontal = OniTokens.space, vertical = 2.dp),
        color = color, fontSize = OniTokens.small, fontWeight = FontWeight.SemiBold, maxLines = 1,
        style = TextStyle(fontFeatureSettings = "tnum"))
}

/** The launcher mark (white ring on blue) drawn in Compose so it needs no resources in the hooked game. */
@Composable
internal fun BrandMark(size: androidx.compose.ui.unit.Dp, ring: Color = Color.White, core: Color = Color(0xFFB8D8FF), background: Color = Color.Transparent) {
    Box(Modifier.size(size).clip(CircleShape).background(background), contentAlignment = Alignment.Center) {
        Box(Modifier.size(size * 0.70f).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) {
            Box(Modifier.size(size * 0.50f).clip(CircleShape).background(IconTint.blue), contentAlignment = Alignment.Center) {
                Box(Modifier.size(size * 0.24f).clip(CircleShape).background(core))
            }
        }
    }
}

/**
 * Gradient header card for the module home, in the manner of HyperOS "About phone".
 * [action] puts the page's main task inside the hero as a solid white button, so it is the first thing a thumb finds.
 */
@Composable
internal fun HeroCard(title: String, subtitle: String, badges: List<String>, action: String? = null, onAction: () -> Unit = {}) {
    val dark = isSystemInDarkTheme()
    val brush = Brush.linearGradient(if (dark) listOf(Color(0xFF1D4FA8), Color(0xFF2B6FE0)) else listOf(Color(0xFF2F7BFF), Color(0xFF6AA8FF)))
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(OniTokens.heroRadius)).background(brush)) {
        // Two faint concentric rings echo the controller's touch ring without any game artwork.
        Box(Modifier.align(Alignment.TopEnd).offset(x = 56.dp, y = (-64).dp).size(200.dp).clip(CircleShape)
            .background(Color.White.copy(alpha = 0.07f)), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
        }
        Column(Modifier.padding(OniTokens.section), verticalArrangement = Arrangement.spacedBy(OniTokens.inset)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OniTokens.inset)) {
                BrandMark(OniTokens.heroMark, background = Color.White.copy(alpha = 0.18f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
                    Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                    Text(subtitle, color = Color.White.copy(alpha = 0.85f), fontSize = OniTokens.caption, maxLines = 3)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
                badges.forEach { Pill(it, color = Color.White, background = Color.White.copy(alpha = 0.2f)) }
            }
            if (action != null) Button(onClick = onAction, modifier = Modifier.fillMaxWidth(), minHeight = OniTokens.target,
                cornerRadius = OniTokens.compactRadius + 4.dp, colors = ButtonDefaults.buttonColors(color = Color.White)) {
                Text(action, color = if (dark) Color(0xFF1D4FA8) else Color(0xFF1F6BFF), fontSize = OniTokens.body, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** Numbered how-to step: circled index, title and optional muted detail. */
@Composable
internal fun GuideStep(index: Int, title: String, detail: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = OniTokens.inset, vertical = OniTokens.gap), horizontalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MiuixTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text("$index", color = MiuixTheme.colorScheme.onTertiaryContainer, fontSize = OniTokens.small, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = OniTokens.label, fontWeight = FontWeight.Medium)
            if (detail != null) Caption(detail)
        }
    }
}

/** Large tinted glyph that introduces a setup step. */
@Composable
internal fun StepHero(heading: String, body: String, icon: ImageVector? = null, label: String? = null) {
    val colors = MiuixTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = OniTokens.space), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(20.dp)).background(colors.tertiaryContainer), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(icon, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(32.dp))
            else if (label != null) Text(label, color = colors.onTertiaryContainer, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text(heading, fontSize = OniTokens.body, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Caption(body, Modifier.padding(horizontal = OniTokens.section))
    }
}

/** Horizontal share bar: each segment's width follows its value. Empty totals draw an even track. */
@Composable
internal fun ShareBar(values: List<Long>, colors: List<Color>, modifier: Modifier = Modifier) {
    val total = values.sum()
    Row(modifier.fillMaxWidth().height(OniTokens.barHeight).clip(RoundedCornerShape(OniTokens.barHeight))
            .background(MiuixTheme.colorScheme.dividerLine),
        horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        if (total > 0) values.forEachIndexed { i, v ->
            if (v > 0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(colors[i]))
        }
    }
}

/** Single-value progress track (0..1). */
@Composable
internal fun ProgressTrack(fraction: Float, modifier: Modifier = Modifier, color: Color = MiuixTheme.colorScheme.primary) {
    Box(modifier.fillMaxWidth().height(OniTokens.barHeight).clip(RoundedCornerShape(OniTokens.barHeight)).background(MiuixTheme.colorScheme.dividerLine)) {
        if (fraction > 0f) Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(OniTokens.barHeight)).background(color))
    }
}

/** Tinted status/instruction panel inside the top of a settings card. First line reads as the headline. */
@Composable
internal fun InfoPanel(lines: List<String>) {
    val colors = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = OniTokens.gap, end = OniTokens.gap, top = OniTokens.gap)
            .clip(RoundedCornerShape(OniTokens.compactRadius)).background(colors.tertiaryContainer).padding(OniTokens.gap),
        horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        Icon(MiuixIcons.Useful.Info, contentDescription = null, tint = colors.onTertiaryContainer, modifier = Modifier.size(18.dp).padding(top = 1.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
            lines.forEachIndexed { i, line ->
                if (i == 0) Text(line, fontSize = OniTokens.rowLabel, fontWeight = FontWeight.Medium, color = colors.onSurface)
                else Text(line, fontSize = OniTokens.caption, color = colors.onSurfaceSecondary)
            }
        }
    }
}

/**
 * Live status at the top of a settings card. The tone picks the tint and the leading dot:
 * green when working, amber while waiting or failing, the neutral info tint otherwise.
 * The first line of [text] is the headline; any further lines are details.
 */
@Composable
internal fun StatusPanel(text: String, tone: Int) {
    val colors = MiuixTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val accent = when (tone) { NativeSettings.OK -> IconTint.green; NativeSettings.WAIT -> IconTint.orange; else -> colors.primary }
    val tint = if (tone == NativeSettings.OK || tone == NativeSettings.WAIT) accent.copy(alpha = if (dark) 0.18f else 0.11f) else colors.tertiaryContainer
    val lines = text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
    if (lines.isEmpty()) return
    Row(Modifier.fillMaxWidth().padding(start = OniTokens.gap, end = OniTokens.gap, top = OniTokens.gap)
            .clip(RoundedCornerShape(OniTokens.compactRadius)).background(tint).padding(horizontal = OniTokens.gap, vertical = OniTokens.gap - 2.dp),
        horizontalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
        Box(Modifier.padding(top = 5.dp).size(8.dp).clip(CircleShape).background(accent))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(lines[0], fontSize = OniTokens.rowLabel, fontWeight = FontWeight.Medium, color = colors.onSurface)
            lines.drop(1).forEach { Text(it, fontSize = OniTokens.caption, color = colors.onSurfaceSecondary) }
        }
    }
}

/** Muted explanatory text under a card, aligned with the section title. Blank lines are skipped. */
@Composable
internal fun Footnotes(lines: List<String>) {
    val shown = lines.filter { it.isNotBlank() }
    if (shown.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(start = OniTokens.inset, end = OniTokens.inset, top = OniTokens.space),
        verticalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
        shown.forEach { Caption(it) }
    }
}
