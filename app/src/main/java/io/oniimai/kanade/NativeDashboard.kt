package io.oniimai.kanade

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal class WidgetState {
    var frame by mutableStateOf(JSONObject())
    var stale by mutableStateOf(true)
    var cover by mutableStateOf<Bitmap?>(null)
    var input by mutableStateOf("")
    var led by mutableStateOf("")
    var output by mutableStateOf("")
    var sensors by mutableStateOf("")
    var minute by mutableLongStateOf(0)
    var width by mutableIntStateOf(2)
    fun update(frame: JSONObject, stale: Boolean, cover: Bitmap?, host: DashboardHost, type: String) {
        this.frame = frame; this.stale = stale; this.cover = cover
        if (type == "connection") { input = host.connectionDescription(); led = host.ledDescription(); output = host.displayDescription() }
        if (type == "sensors") { val d = host.diagnostic(); sensors = "T ${java.lang.Long.bitCount(d[1])}/34   B ${Integer.bitCount(d[0].toInt() and 255)}/8   P1 ${if (d[0] and 256L != 0L) "ON" else "OFF"}" }
        if (type == "clock") minute = System.currentTimeMillis() / 60000
    }
}

internal object NativeDashboard {
    @JvmStatic fun pageColor(activity: Activity): Int = if (activity.resources.configuration.uiMode and 48 == 32) 0xff000000.toInt() else 0xfff7f7f7.toInt()
    @JvmStatic fun header(activity: Activity, editing: Boolean, preview: Boolean, demo: Boolean, action: Runnable): View = ComposeHost(activity) {
        Column(Modifier.background(MiuixTheme.colorScheme.background)) {
            PageHeader(if (editing) tr("위젯 편집", "编辑小组件") else tr("대시보드", "仪表盘"), action = {
                Action(if (editing) tr("저장", "保存") else tr("편집", "编辑"), editing, onClick = action::run)
            })
            if (editing || preview) Caption(when {
                editing -> tr("끌어서 이동 · 모서리로 크기 변경 · 눌러서 옵션", "拖动移动 · 拖角调整大小 · 点击打开选项")
                demo -> tr("미리보기 · USB 연결 OFF · 실제 게임 데이터 없음", "预览 · USB 未连接 · 无实际游戏数据")
                else -> tr("저장한 배치는 외부 화면 연결 시에도 사용됩니다", "保存的布局也用于外接屏幕模式")
            }, Modifier.padding(start = OniTokens.inset, end = OniTokens.inset, bottom = OniTokens.gap))
        }
    }
    @JvmStatic fun footer(activity: Activity, labels: Array<String>, actions: Array<Runnable>, primaryFirst: Boolean): View = ComposeHost(activity) {
        Row(Modifier.background(MiuixTheme.colorScheme.background).padding(OniTokens.inset).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
            labels.forEachIndexed { index, label -> Action(label, primaryFirst && index == 0, Modifier.weight(1f), onClick = actions[index]::run) }
        }
    }
    @JvmStatic fun widget(activity: Activity, type: String, host: DashboardHost, state: WidgetState): View = ComposeHost(activity) {
        Card(Modifier.fillMaxSize(), insideMargin = PaddingValues(OniTokens.inset)) {
            Text(DashboardView.title(type), fontSize = OniTokens.caption, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(OniTokens.space))
            Box(Modifier.weight(1f).fillMaxWidth()) { WidgetContent(type, host, state) }
        }
    }
}

@Composable private fun WidgetContent(type: String, host: DashboardHost, state: WidgetState) {
    val data = state.frame
    val live = !state.stale && data.optBoolean("game", false)
    fun number(key: String) = if (live) data.optLong(key).toString() else "—"
    when (type) {
        "song" -> SongWidget(state, live)
        "score" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Caption("Achievement")
            Metric(if (live) String.format(Locale.US, "%.4f%%", data.optDouble("achievement")) else "—", large = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallMetric("Combo", number("combo"))
                SmallMetric(tr("DX 잔여", "DX 剩余"), number("dx"))
            }
        }
        "judgments" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            listOf("critical" to "Critical", "perfect" to "Perfect", "great" to "Great", "good" to "Good", "miss" to "Miss").forEach { (key, name) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(name, fontSize = OniTokens.rowLabel)
                    Text(number(key), fontSize = OniTokens.rowLabel, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, style = TextStyle(fontFeatureSettings = "tnum"))
                }
            }
        }
        "timing" -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
            listOf("Fast" to "fast", "Late" to "late").forEach { (label, key) ->
                Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(OniTokens.compactRadius)).background(MiuixTheme.colorScheme.secondaryContainer).padding(OniTokens.space),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) { Caption(label); Metric(number(key), true) }
            }
        }
        "sensors" -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            val colors = MiuixTheme.colorScheme
            AndroidView(factory = { SensorBoard(host.activity(), host).withoutFooter().gameTheme().compact(true) }, modifier = Modifier.weight(1f).fillMaxWidth(),
                update = { it.palette(colors.primary.toArgb(), colors.secondaryContainer.toArgb(), colors.surface.toArgb(), colors.onSurface.toArgb(), colors.onSurfaceVariantSummary.toArgb()) })
            Text(state.sensors, fontSize = OniTokens.sensorLabel, maxLines = 1, color = colors.onSurfaceVariantSummary)
        }
        "connection" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            listOf(tr("입력", "输入") to state.input, "LED" to state.led, tr("화면", "屏幕") to state.output).forEach { (name, value) ->
                if (state.width == 4) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.inset)) {
                    Caption(name, Modifier.width(44.dp)); Text(value, fontSize = OniTokens.caption, maxLines = 2, overflow = TextOverflow.Ellipsis)
                } else Column { Caption(name); Text(value.replace('\n', ' '), fontSize = OniTokens.small, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
        "clock" -> {
            val context = LocalContext.current
            val date = remember(state.minute) { Date(state.minute * 60000) }
            val format = if (android.text.format.DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm"
            if (state.width == 4) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Metric(SimpleDateFormat(format, uiLocale()).format(date), true)
                Caption(SimpleDateFormat("M.d E", uiLocale()).format(date))
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                Metric(SimpleDateFormat(format, uiLocale()).format(date), true)
                Spacer(Modifier.height(OniTokens.space)); Caption(SimpleDateFormat("M.d E", uiLocale()).format(date))
            }
        }
    }
}

@Composable private fun SongWidget(state: WidgetState, live: Boolean) {
    val data = state.frame
    val title = data.optString("title").ifBlank { when (data.optString("scene")) {
        "List" -> tr("곡 선택", "选择歌曲")
        "Result" -> tr("플레이 결과", "游玩结果")
        else -> tr("곡 시작 대기", "等待歌曲开始")
    } }
    val artist = data.optString("artist").ifBlank { tr("게임에서 곡을 선택해 주세요", "请在游戏中选择歌曲") }
    val cover = remember(state.cover) { (state.cover ?: GameAssets.placeholder()).asImageBitmap() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
            Image(cover, tr("곡 앨범 이미지", "歌曲封面"), Modifier.size(48.dp).clip(RoundedCornerShape(OniTokens.artworkRadius)), contentScale = ContentScale.Crop)
            Column { Caption("LEVEL"); Text(data.optString("level").ifBlank { "—" }, fontSize = OniTokens.metricSmall, fontWeight = FontWeight.SemiBold) }
        }
        Text(title, fontSize = OniTokens.label, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(artist, fontSize = OniTokens.small, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun Metric(value: String, large: Boolean = false) {
    Text(value, fontSize = if (large) OniTokens.metric else OniTokens.metricSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
        autoSize = TextAutoSize.StepBased(OniTokens.metricMinimum, if (large) OniTokens.metric else OniTokens.metricSmall),
        style = TextStyle(fontFeatureSettings = "tnum"), color = if (large && value != "—") MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
}
@Composable private fun SmallMetric(label: String, value: String) { Column { Caption(label); Metric(value) } }

private fun uiLocale() = if (UiText.language() == "zh-Hans") Locale.SIMPLIFIED_CHINESE else Locale.KOREAN
