package io.oniimai.kanade

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * The system toast can truncate localized text. This themed, non-modal card wraps all lines.
 * Its text scrolls at large font scales / short landscape heights, with OK kept outside the scroll.
 */
@Composable
internal fun InlineNotice(text: String, onDismiss: () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        .imePadding().padding(OniTokens.inset), contentAlignment = Alignment.BottomCenter) {
        val heightLimit = if (constraints.hasBoundedHeight) maxHeight else 360.dp
        Card(Modifier.widthIn(max = OniTokens.width).fillMaxWidth().heightIn(max = heightLimit),
            insideMargin = PaddingValues(OniTokens.inset)) {
            Column(Modifier.fillMaxWidth()) {
                Text(text, modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite },
                    fontSize = OniTokens.label, color = MiuixTheme.colorScheme.onSurface,
                    softWrap = true, maxLines = Int.MAX_VALUE)
                Spacer(Modifier.height(OniTokens.space))
                Action(str(Msg.COMMON_OK), modifier = Modifier.align(Alignment.End), onClick = onDismiss)
            }
        }
    }
}
