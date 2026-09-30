package io.oniimai.kanade

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.SuperArrow
import top.yukonga.miuix.kmp.extra.SuperSwitch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Screen content only. Nothing here touches Activity, SharedPreferences, Bitmap or Android Views:
 * NativeUi, NativeDashboard and LicenseUi own those and pass plain values and callbacks in.
 */

/**
 * MIUI page chrome: a large title that collapses into a centred small title while the list
 * scrolls. The nested-scroll connection sits on the wrapper, so every scrollable child
 * (settings list, license text, setup steps) drives the header without extra wiring.
 */
@Composable internal fun Page(title: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val bar = MiuixScrollBehavior()
    Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = OniTokens.width).fillMaxSize().nestedScroll(bar.nestedScrollConnection)) {
            TopAppBar(title = title, navigationIcon = { if (back != null) BackButton(back) }, scrollBehavior = bar,
                defaultWindowInsetsPadding = false, horizontalPadding = OniTokens.titleInset)
            content()
        }
    }
}

@Composable internal fun HomeScreen(language: String, onLaunch: () -> Unit, onPreview: () -> Unit, onLanguage: () -> Unit, onLicenses: () -> Unit) {
    Page(tr("Oniimai", "Oniimai")) {
        LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
            item(key = "hero") {
                // The large page title already says "Oniimai"; the hero describes what the module does.
                // Launching the game is the reason to open this app, so it is the hero's button rather than a list row.
                HeroCard(tr("컨트롤러 모듈", "控制器模块"), tr("KanadeDX 전용 · 터치·버튼 입력, RGB 조명, 외부 화면", "KanadeDX 专用 · 触摸与按钮输入、RGB 灯光、外接屏幕"),
                    listOf("v${BuildConfig.VERSION_NAME}", "LSPosed API 102"), action = tr("KanadeDX 열기", "打开 KanadeDX"), onAction = onLaunch)
            }
            item(key = "main") { Card {
                SuperArrow(title = tr("대시보드 미리보기", "预览仪表盘"), summary = tr("위젯 배치와 크기를 편집합니다", "编辑小组件的布局与大小"),
                    leftAction = { RowIcon(IconTint.purple, MiuixIcons.Useful.Edit) }, onClick = onPreview)
            } }
            item(key = "app") { SectionTitle(tr("앱 설정", "应用设置")); Card {
                SuperArrow(title = tr("언어", "语言"), rightText = language, leftAction = { RowIcon(IconTint.green, label = "文") }, onClick = onLanguage)
                SuperArrow(title = tr("라이선스 · 출처", "许可与来源"), summary = "GPL-3.0 · MPL-2.0",
                    leftAction = { RowIcon(IconTint.slate, MiuixIcons.Useful.Info) }, onClick = onLicenses)
            } }
            item(key = "guide") { SectionTitle(tr("사용 안내", "使用说明")); Card(insideMargin = PaddingValues(vertical = OniTokens.space)) {
                GuideStep(1, tr("LSPosed에서 모듈 활성화", "在 LSPosed 中启用模块"), tr("적용 대상으로 KanadeDX를 선택하세요.", "作用域请选择 KanadeDX。"))
                GuideStep(2, tr("KanadeDX 실행", "启动 KanadeDX"), tr("오른쪽 위 Oniimai 설정 버튼은 끌어서 옮길 수 있습니다.", "右上角的 Oniimai 设置按钮可拖动移动。"))
                GuideStep(3, tr("게임 안 Onii 설정", "游戏内 Onii 设置"), tr("USB 연결, 외부 화면과 LED를 조절합니다. 미리보기는 USB에 연결하지 않습니다.", "调整 USB 连接、外接屏幕和 LED。预览不会连接 USB。"))
            } }
            item(key = "footer") {
                Box(Modifier.fillMaxWidth().padding(OniTokens.inset), contentAlignment = Alignment.Center) { Caption("KanadeDX 1.60 / 1.65 · Android 9+") }
            }
        }
    }
}

@Composable internal fun SettingsScreen(tab: Int, groups: List<NativeSettings.Group>, language: String, onTab: (Int) -> Unit, refresh: () -> Unit,
                                        close: () -> Unit, onLanguage: () -> Unit, onLicenses: () -> Unit,
                                        listState: @Composable (Int) -> LazyListState = { rememberLazyListState() }) {
    Page(tr("컨트롤러 설정", "控制器设置"), back = close) {
        TabRow(listOf(tr("연결", "连接"), tr("화면", "屏幕"), tr("버튼", "按钮"), "LED"), tab,
            modifier = Modifier.padding(horizontal = OniTokens.inset), height = OniTokens.target, onTabSelected = onTab)
        key(tab) {
            LazyColumn(state = listState(tab), modifier = Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
                item(key = "hint") {
                    Caption(tr("자동 저장 · 설정 중에는 게임 입력 일시 정지", "自动保存 · 设置时暂停游戏输入"),
                        Modifier.padding(horizontal = OniTokens.inset))
                }
                itemsIndexed(groups, key = { index, _ -> "group-$index" }) { _, group -> SettingsGroup(group, refresh) }
                // App-level preferences sit together at the end of the first tab instead of bracketing the hardware groups.
                if (tab == 0) item(key = "general") {
                    Column {
                        SectionTitle(tr("일반", "通用"))
                        Card {
                            SuperArrow(tr("언어", "语言"), rightText = language, leftAction = { RowIcon(IconTint.green, label = "文") }, onClick = onLanguage)
                            SuperArrow(title = tr("라이선스 · 출처", "许可与来源"), summary = "GPL-3.0 · MPL-2.0", leftAction = { RowIcon(IconTint.slate, MiuixIcons.Useful.Info) }, onClick = onLicenses)
                        }
                    }
                }
            }
        }
    }
}

/**
 * One settings group, arranged the way HyperOS Settings does it:
 * - notes before the first control (live status, instructions) become an info panel at the top of the card;
 * - controls follow, uninterrupted;
 * - remaining notes (explanations) move below the card as a muted footnote;
 * - a group with no controls is plain footnote text, without an empty-looking card.
 * Row order among controls is untouched, so every callback keeps its meaning.
 */
@Composable
private fun SettingsGroup(group: NativeSettings.Group, refresh: () -> Unit) {
    val statuses = group.rows.filter { it.tone != NativeSettings.NONE && it.summary.isNotBlank() }
    val rows = group.rows.filter { it.tone == NativeSettings.NONE }
    val first = rows.indexOfFirst { it.toggle != null || it.action != null }
    Column {
        SectionTitle(group.title)
        if (first < 0 && statuses.isEmpty()) {
            Footnotes(rows.map { it.summary })
        } else {
            // With no controls, every note is explanation and goes under the card.
            val split = if (first < 0) 0 else first
            val lead = rows.take(split).map { it.summary }.filter { it.isNotBlank() }
            val rest = rows.drop(split)
            Card(insideMargin = PaddingValues(bottom = if (rest.any { it.toggle != null || it.action != null }) 0.dp else OniTokens.gap)) {
                statuses.forEach { StatusPanel(it.summary, it.tone) }
                if (lead.isNotEmpty()) InfoPanel(lead)
                rest.forEach { row ->
                    when {
                        row.toggle != null -> SuperSwitch(title = row.title, summary = row.summary.takeIf { it.isNotEmpty() }, checked = row.checked,
                            insideMargin = DENSE_ROW, onCheckedChange = { row.toggle.accept(it); refresh() })
                        row.action != null -> SuperArrow(title = row.title, summary = row.summary.takeIf { it.isNotEmpty() },
                            insideMargin = DENSE_ROW, onClick = { row.action.run(); refresh() })
                    }
                }
            }
            Footnotes(rest.filter { it.toggle == null && it.action == null }.map { it.summary })
        }
    }
}
/** Settings lists are long; slightly tighter rows (still above the 48dp target) keep more of a group on screen. */
private val DENSE_ROW = PaddingValues(horizontal = OniTokens.inset, vertical = OniTokens.gap)

@Composable internal fun ChoiceScreen(title: String, options: Array<String>, selected: Int, onBack: () -> Unit, onPick: (Int) -> Unit) {
    Page(title, back = onBack) {
        LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), overscrollEffect = null) {
            item { Card { options.forEachIndexed { index, option ->
                // Catalog entries arrive as "title\ndescription"; show the second line as a summary.
                val lines = option.split('\n', limit = 2)
                OptionRow(lines[0], selected = index == selected, summary = lines.getOrNull(1), onClick = { onPick(index) })
            } } }
        }
    }
}

@Composable internal fun NumberScreen(title: String, current: Int, min: Int, max: Int, onBack: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val number = text.toIntOrNull()
    val valid = number != null && number in min..max
    val digits = maxOf(min.toString().length, max.toString().length)
    val save: () -> Unit = { if (valid) onSave(number!!) }
    Page(title, back = onBack) {
        Column(Modifier.padding(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
            // Digits only (paste included), bounded by the widest allowed value; the keyboard's Done key saves.
            TextField(value = text, onValueChange = { text = it.filter(Char::isDigit).take(digits) }, label = "$min–$max",
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                borderColor = if (valid || text.isEmpty()) MiuixTheme.colorScheme.primary else ERROR_TEXT,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }))
            Caption(if (valid || text.isEmpty()) tr("범위: $min–$max", "范围：$min–$max") else tr("$min–$max 사이의 숫자를 입력하세요", "请输入 $min–$max 之间的数字"),
                Modifier.padding(horizontal = OniTokens.space), color = if (valid || text.isEmpty()) MiuixTheme.colorScheme.onSurfaceVariantSummary else ERROR_TEXT)
            Spacer(Modifier.height(OniTokens.space))
            Action(tr("저장", "保存"), true, Modifier.fillMaxWidth(), enabled = valid) { save() }
        }
    }
}

@Composable internal fun BrightnessScreen(current: Int, onBack: () -> Unit, onChange: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(current.coerceIn(0, 100).toFloat()) }
    var sent by remember { mutableIntStateOf(current) }
    // Live preview, but only when the whole percentage changes: a drag emits a value per frame
    // and each accepted value is written to the LED controller.
    val send: (Int) -> Unit = { next -> if (next != sent) { sent = next; onChange(next) } }
    Page(tr("LED 밝기", "LED 亮度"), back = onBack) {
        Card(Modifier.padding(OniTokens.inset), insideMargin = PaddingValues(OniTokens.inset)) {
            Text("${value.toInt()}%", fontSize = OniTokens.displayMetric, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(OniTokens.gap))
            Slider(value = value, onValueChange = { value = it; send(it.toInt()) }, valueRange = 0f..100f,
                onValueChangeFinished = { send(value.toInt()) },
                modifier = Modifier.heightIn(min = OniTokens.target).semantics { contentDescription = tr("LED 밝기", "LED 亮度") })
            Spacer(Modifier.height(OniTokens.space))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                listOf(25, 50, 75, 100).forEach { preset ->
                    Action("$preset%", preset == value.toInt(), Modifier.weight(1f)) { value = preset.toFloat(); send(preset) }
                }
            }
        }
        Caption(tr("변경 즉시 LED에 적용되고 저장됩니다.", "更改会立即应用到 LED 并保存。"), Modifier.padding(horizontal = OniTokens.titleInset))
    }
}

/** Values chosen in first-run setup, written by the caller only when the user finishes. */
internal class SetupChoices(var external: Boolean, var clockwise: Boolean, var auto: Boolean, var led: Boolean, var aime: Boolean)

@Composable internal fun SetupScreen(initialLanguage: String, initial: SetupChoices, onLanguage: (String) -> Unit, onCancel: () -> Unit, onFinish: (SetupChoices) -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    var language by remember { mutableStateOf(initialLanguage) }
    var external by remember { mutableStateOf(initial.external) }
    var clockwise by remember { mutableStateOf(initial.clockwise) }
    var auto by remember { mutableStateOf(initial.auto) }
    var led by remember { mutableStateOf(initial.led) }
    var aime by remember { mutableStateOf(initial.aime) }
    key(language) {
        Page(if (step == 0) "Language / 语言" else if (step == 1) tr("모니터 방향", "显示器方向") else tr("컨트롤러 연결", "控制器连接"),
            back = { if (step == 0) onCancel() else step-- }) {
            LazyColumn(Modifier.weight(1f).miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
                item {
                    Column(Modifier.padding(horizontal = OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                        StepIndicator(step, 3)
                        Caption(tr("초기 설정", "初始设置") + " · ${step+1} / 3")
                    }
                }
                item(key = "hero-$step") {
                    when (step) {
                        0 -> StepHero(tr("사용할 언어를 고르세요", "选择使用的语言"), tr("언어는 설정에서 언제든 변경할 수 있습니다.", "随时可在设置中更改语言。"), label = "文")
                        1 -> StepHero(tr("외부 모니터 출력", "外接显示器输出"), tr("가로 16:9 모니터에 게임을 90° 회전해 채웁니다. 폰에는 위젯 대시보드가 표시됩니다.", "将游戏旋转 90° 填满横向 16:9 显示器。手机显示小组件仪表盘。"), icon = MiuixIcons.Useful.Refresh)
                        else -> StepHero(tr("컨트롤러 연결", "控制器连接"), tr("장치 이름으로 Touch·IO4·LED·NFC 포트를 찾습니다. USB 연결 권한을 허용해 주세요.", "按名称查找 Touch、IO4、LED 和 NFC 端口。请授予 USB 连接权限。"), icon = MiuixIcons.Useful.Settings)
                    }
                }
                item { Card {
                    when (step) {
                        0 -> UiLanguage.NAMES.forEachIndexed { i, title ->
                            val code = if (i == 0) "ko" else "zh-Hans"
                            OptionRow(title, selected = language == code, onClick = {
                                if (language != code) { language = code; onLanguage(code) }
                            })
                        }
                        1 -> SuperSwitch(title = tr("외부 화면으로 게임 출력", "向外接屏幕输出游戏"), summary = tr("모니터 연결 시 자동 출력", "连接显示器时自动输出"), checked = external, onCheckedChange = { external = it })
                        else -> {
                            SuperSwitch(title = tr("자동 연결", "自动连接"), checked = auto, onCheckedChange = { auto = it })
                            SuperSwitch(title = tr("게임 LED 연동", "游戏 LED 联动"), checked = led, onCheckedChange = { led = it })
                            SuperSwitch(title = tr("Aime 카드 인식", "读取 Aime 卡"), checked = aime, onCheckedChange = { aime = it })
                        }
                    }
                } }
                // Rotation only matters with external output on; keep it visible but disabled otherwise.
                if (step == 1) item {
                    Column {
                        SectionTitle(tr("회전 방향", "旋转方向"))
                        Card {
                            OptionRow(tr("90° · 시계 방향", "90° · 顺时针"), selected = clockwise, enabled = external, onClick = { clockwise = true })
                            OptionRow(tr("270° · 반대 방향", "270° · 逆时针"), selected = !clockwise, enabled = external, onClick = { clockwise = false })
                        }
                    }
                }
            }
            Action(if(step == 2) tr("설정 완료", "完成设置") else tr("다음", "下一步"), true,
                Modifier.fillMaxWidth().padding(horizontal = OniTokens.inset, vertical = OniTokens.gap)) {
                if (step < 2) step++ else onFinish(SetupChoices(external, clockwise, auto, led, aime))
            }
        }
    }
}

@Composable internal fun LicenseScreen(version: String, sourceUrl: String, files: List<String>, onBack: () -> Unit, onSource: () -> Unit, onFile: (String) -> Unit) {
    Page(tr("라이선스 · 출처", "许可与来源"), back = onBack) {
        LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
            item { Card(insideMargin = PaddingValues(OniTokens.inset)) {
                Text("Oniimai $version · GPL-3.0-only")
                Spacer(Modifier.height(OniTokens.gap))
                Text(tr("비공식 LSPosed 모듈 · kyarameru0\n코드·테스트·문서는 Codex를 사용해 작성·수정했습니다. 외부 코드의 저작권과 라이선스는 각 권리자에게 있습니다.", "非官方 LSPosed 模块 · kyarameru0\n代码、测试和文档使用 Codex 编写和修改。第三方代码的版权和许可归相应权利人所有。"))
                Spacer(Modifier.height(OniTokens.gap))
                Text(tr("GPL 조건에 따라 사용·수정·재배포할 수 있으며 보증은 제공되지 않습니다. FeliCa 디코더와 외부 구성요소의 조건도 적용됩니다. 게임·서비스·상표의 권한은 이 라이선스에 포함되지 않습니다.", "可依 GPL 条款使用、修改和再分发，不提供担保。FeliCa 解码器及第三方组件的许可条件同样适用。本许可不授予游戏、服务或商标的权利。"))
            } }
            item { Card {
                SuperArrow(title = tr("소스 코드와 빌드 방법", "源代码与构建说明"), summary = sourceUrl, onClick = onSource)
            } }
            item { Card(insideMargin = PaddingValues(OniTokens.inset)) {
                Text(tr("APK와 함께 받은 같은 버전의 Source ZIP에도 소스와 빌드 방법이 있습니다. 저장소가 비공개라면 배포자에게 해당 소스를 요청하세요. 아래 원문은 인터넷 없이 볼 수 있습니다.", "随 APK 提供的同版本 Source ZIP 也包含源代码和构建说明。若仓库为私有，请向分发者索取对应源代码。以下许可原文可离线查看。"))
            } }
            item { Card {
                files.forEach { name -> SuperArrow(title = name, onClick = { onFile(name) }) }
            } }
        }
    }
}

@Composable internal fun LicenseDocumentScreen(name: String, paragraphs: List<String>?, failed: Boolean, onBack: () -> Unit) {
    Page(name, back = onBack) {
        LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
            if (failed) item { Caption(color = ERROR_TEXT, text = tr("동봉된 원문을 불러오지 못했습니다. 같은 버전의 Source ZIP에서 licenses 폴더를 확인하세요.", "无法载入随附原文。请查看同版本 Source ZIP 中的 licenses 文件夹。")) }
            else if (paragraphs == null) item { Caption(tr("불러오는 중…", "正在载入…"), Modifier.padding(horizontal = OniTokens.inset)) }
            else items(paragraphs) { paragraph -> SelectionContainer { Text(paragraph, modifier = Modifier.fillMaxWidth()) } }
        }
    }
}

/* ---------------- Dashboard ---------------- */

@Composable internal fun DashboardHeader(editing: Boolean, preview: Boolean, demo: Boolean, action: () -> Unit) {
    Column(Modifier.background(MiuixTheme.colorScheme.background)) {
        PageHeader(if (editing) tr("위젯 편집", "编辑小组件") else tr("대시보드", "仪表盘"), action = {
            HeaderAction(if (editing) tr("저장", "保存") else tr("편집", "编辑"), editing, onClick = action)
        })
        if (editing || preview) Caption(when {
            editing -> tr("끌어서 이동 · 모서리로 크기 변경 · 눌러서 옵션", "拖动移动 · 拖角调整大小 · 点击打开选项")
            demo -> tr("미리보기 · USB 연결 OFF · 실제 게임 데이터 없음", "预览 · USB 未连接 · 无实际游戏数据")
            else -> tr("저장한 배치는 외부 화면 연결 시에도 사용됩니다", "保存的布局也用于外接屏幕模式")
        }, Modifier.padding(start = OniTokens.inset, end = OniTokens.inset, bottom = OniTokens.gap))
    }
}

@Composable internal fun DashboardFooter(labels: List<String>, actions: List<() -> Unit>, primaryFirst: Boolean) {
    Row(Modifier.background(MiuixTheme.colorScheme.background).padding(OniTokens.inset).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        labels.forEachIndexed { index, label -> Action(label, primaryFirst && index == 0, Modifier.weight(1f), onClick = actions[index]) }
    }
}

/** Live values for one widget. The Android host fills it; the composables only read it. */
internal class WidgetState {
    var frame by mutableStateOf(JSONObject())
    var stale by mutableStateOf(true)
    var cover by mutableStateOf<ImageBitmap?>(null)
    var input by mutableStateOf("")
    var led by mutableStateOf("")
    var output by mutableStateOf("")
    var sensors by mutableStateOf("")
    var minute by mutableLongStateOf(0)
    var is24Hour by mutableStateOf(true)
    var width by mutableIntStateOf(2)
    var height by mutableIntStateOf(2)
    /** Identity of the last converted cover, so a repeated frame does not re-wrap the same Bitmap. */
    internal var coverSource: Any? = null
}

@Composable internal fun DashboardWidgetCard(type: String, title: String, state: WidgetState, sensorBoard: @Composable (Modifier) -> Unit) {
    Card(Modifier.fillMaxSize(), insideMargin = PaddingValues(OniTokens.inset)) {
        val live = !state.stale && state.frame.optBoolean("game", false)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
            Text(title, Modifier.weight(1f), fontSize = OniTokens.caption, fontWeight = FontWeight.Medium, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Game-data widgets show a small green dot while numbers are live.
            if (live && type in LIVE_TYPES) Dot(IconTint.green)
        }
        Spacer(Modifier.height(OniTokens.space))
        Box(Modifier.weight(1f).fillMaxWidth()) { WidgetContent(type, state, sensorBoard) }
    }
}

@Composable private fun WidgetContent(type: String, state: WidgetState, sensorBoard: @Composable (Modifier) -> Unit) {
    val data = state.frame
    val live = !state.stale && data.optBoolean("game", false)
    fun number(key: String) = if (live) data.optLong(key).toString() else "—"
    when (type) {
        "song" -> SongWidget(state, live)
        "score" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            val achievement = data.optDouble("achievement")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Caption("Achievement", Modifier.weight(1f))
                if (live) Pill(rank(achievement))
            }
            Column(verticalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) {
                Metric(if (live) String.format(Locale.US, "%.4f%%", achievement) else "—", large = true)
                ProgressTrack(if (live) (achievement / 101.0).toFloat() else 0f, Modifier.height(4.dp))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SmallMetric("Combo", number("combo"))
                SmallMetric(tr("DX 잔여", "DX 剩余"), number("dx"))
            }
        }
        "judgments" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            ShareBar(JUDGMENT_KEYS.map { if (live) data.optLong(it) else 0L }, JUDGMENT_KEYS.indices.map { Color(GameUi.JUDGMENT[it]) }, Modifier.height(4.dp))
            listOf("critical" to "Critical", "perfect" to "Perfect", "great" to "Great", "good" to "Good", "miss" to "Miss").forEachIndexed { index, (key, name) ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space), verticalAlignment = Alignment.CenterVertically) {
                    Dot(Color(GameUi.JUDGMENT[index]))
                    Text(name, Modifier.weight(1f), fontSize = OniTokens.rowLabel, maxLines = 1)
                    // Misses are the one count worth noticing at a glance.
                    val miss = key == "miss" && live && data.optLong(key) > 0
                    Text(number(key), fontSize = OniTokens.rowLabel, fontWeight = FontWeight.Medium, textAlign = TextAlign.End, maxLines = 1,
                        style = TextStyle(fontFeatureSettings = "tnum"), color = if (miss) ERROR_TEXT else MiuixTheme.colorScheme.onSurface)
                }
            }
        }
        "timing" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(OniTokens.space)) {
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                listOf(Triple("Fast", "fast", 5), Triple("Late", "late", 6)).forEach { (label, key, tone) ->
                    Column(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(OniTokens.compactRadius)).background(MiuixTheme.colorScheme.secondaryContainer).padding(OniTokens.space),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OniTokens.space / 2)) { Dot(Color(GameUi.JUDGMENT[tone])); Caption(label) }
                        Metric(number(key), true)
                    }
                }
            }
            // Fast ↔ Late balance: an even split means timing is centred.
            ShareBar(listOf("fast", "late").map { if (live) data.optLong(it) else 0L }, listOf(Color(GameUi.JUDGMENT[5]), Color(GameUi.JUDGMENT[6])), Modifier.height(4.dp))
        }
        "sensors" -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            sensorBoard(Modifier.weight(1f).fillMaxWidth())
            Text(state.sensors, fontSize = OniTokens.small, maxLines = 1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                autoSize = TextAutoSize.StepBased(OniTokens.sensorLabel, OniTokens.small), style = TextStyle(fontFeatureSettings = "tnum"))
        }
        "connection" -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            listOf(tr("입력", "输入") to state.input, "LED" to state.led, tr("화면", "屏幕") to state.output).forEach { (name, value) ->
                // Narrow tiles show each status's headline only; the wide tile has room for its second line.
                val lines = value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                    Box(Modifier.padding(top = 5.dp).size(OniTokens.dot).clip(CircleShape).background(connectionTone(value)))
                    Column(Modifier.weight(1f)) {
                        Caption(name)
                        Text(if (state.width == 4) lines.joinToString(" · ") else lines.firstOrNull() ?: "—", fontSize = if (state.width == 4) OniTokens.caption else OniTokens.small,
                            fontWeight = FontWeight.Medium, maxLines = if (state.width == 4) 2 else 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        "clock" -> {
            val date = remember(state.minute) { Date(state.minute * 60000) }
            val time = SimpleDateFormat(if (state.is24Hour) "HH:mm" else "h:mm", uiLocale()).format(date)
            val day = SimpleDateFormat(tr("M월 d일", "M月d日"), uiLocale()).format(date)
            val weekday = SimpleDateFormat("EEEE", uiLocale()).format(date)
            if (state.width == 4) Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OniTokens.section)) {
                ClockTime(time, Modifier.weight(1f), OniTokens.clock)
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(day, fontSize = OniTokens.body, fontWeight = FontWeight.Medium, maxLines = 1)
                    Caption(weekday)
                }
            } else Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                ClockTime(time)
                Spacer(Modifier.height(OniTokens.space)); Caption("$day · $weekday")
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
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(OniTokens.space)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
            Artwork(state.cover, Modifier.size(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Caption("LEVEL")
                val level = data.optString("level")
                if (level.isNotBlank()) Pill(level) else Text("—", fontSize = OniTokens.metricSmall, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontSize = OniTokens.label, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = OniTokens.label * 1.25f)
            Text(artist, fontSize = OniTokens.small, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Song artwork, or a code-drawn equalizer mark when the game has none to offer. */
@Composable private fun Artwork(cover: ImageBitmap?, modifier: Modifier) {
    val shape = RoundedCornerShape(OniTokens.compactRadius)
    if (cover != null) {
        Image(cover, tr("곡 앨범 이미지", "歌曲封面"), modifier.clip(shape), contentScale = ContentScale.Crop)
        return
    }
    val night = isSystemInDarkTheme()
    Box(modifier.clip(shape).background(Color(if (night) GameUi.NIGHT_PALE else GameUi.PALE)), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxSize(0.5f), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(0.38f, 0.66f, 0.9f, 0.56f).forEach { h ->
                Box(Modifier.weight(1f).fillMaxHeight(h).clip(RoundedCornerShape(50)).background(Color(GameUi.accent(night))))
            }
        }
    }
}

@Composable private fun Metric(value: String, large: Boolean = false) {
    Text(value, fontSize = if (large) OniTokens.metric else OniTokens.metricSmall, fontWeight = FontWeight.SemiBold, maxLines = 1,
        autoSize = TextAutoSize.StepBased(OniTokens.metricMinimum, if (large) OniTokens.metric else OniTokens.metricSmall),
        style = TextStyle(fontFeatureSettings = "tnum"), color = if (large && value != "—") MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface)
}
@Composable private fun ClockTime(value: String, modifier: Modifier = Modifier, size: TextUnit = OniTokens.displayMetric) {
    Text(value, modifier, fontSize = size, fontWeight = FontWeight.Medium, maxLines = 1,
        autoSize = TextAutoSize.StepBased(OniTokens.metricMinimum, size), style = TextStyle(fontFeatureSettings = "tnum"))
}
/**
 * Status colour for the device widget. The host reports plain text, so read the few words that
 * mean "working" or "off"; anything else (waiting, retrying, errors) is amber.
 */
private fun connectionTone(value: String): Color {
    val head = value.lineSequence().firstOrNull().orEmpty()
    return when {
        head.contains("OFF") || head.contains(tr("미리보기", "预览")) || head.contains(tr("휴대폰 화면", "手机屏幕")) -> Color(GameUi.MUTED)
        head.contains(tr("연결됨", "已连接")) || head.contains(tr("연동 중", "联动中")) || head.contains("16:9") -> IconTint.green
        else -> IconTint.orange
    }
}
private val LIVE_TYPES = setOf("song", "score", "judgments", "timing")
private val JUDGMENT_KEYS = listOf("critical", "perfect", "great", "good", "miss")
/** maimai DX-style rank bands for the achievement badge. */
private fun rank(a: Double) = when {
    a >= 100.5 -> "SSS+"; a >= 100.0 -> "SSS"; a >= 99.5 -> "SS+"; a >= 99.0 -> "SS"; a >= 98.0 -> "S+"; a >= 97.0 -> "S"
    a >= 94.0 -> "AAA"; a >= 90.0 -> "AA"; a >= 80.0 -> "A"; a >= 75.0 -> "BBB"; a >= 70.0 -> "BB"; a >= 60.0 -> "B"; a >= 50.0 -> "C"; else -> "D"
}
@Composable private fun Dot(color: Color) { Box(Modifier.size(OniTokens.dot).clip(CircleShape).background(color)) }
@Composable private fun SmallMetric(label: String, value: String) { Column { Caption(label); Metric(value) } }

private fun uiLocale() = if (UiText.language() == "zh-Hans") Locale.SIMPLIFIED_CHINESE else Locale.KOREAN
