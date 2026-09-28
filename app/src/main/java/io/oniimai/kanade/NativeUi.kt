// Modified 2026-09-29 (UI refinement pass, see CHANGES-UI.md). Original: KanadeDX-Oniimai 1.1.0-rc5 @ 1c9c518b.
package io.oniimai.kanade

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.icons.useful.Edit
import top.yukonga.miuix.kmp.icon.icons.useful.Info
import top.yukonga.miuix.kmp.icon.icons.useful.Play
import top.yukonga.miuix.kmp.icon.icons.useful.Refresh
import top.yukonga.miuix.kmp.icon.icons.useful.Settings
import java.util.function.Consumer
import java.util.function.IntConsumer

/** Entry points are callable from the small Java Activity and the injected Unity session. */
internal object NativeUi {
    @JvmStatic fun settingsShortcutWidthDp(): Int = OniTokens.shortcutWidth.value.toInt()
    /** The parent owns drag gestures; Compose supplies the themed, accessible action. */
    @JvmStatic fun floatingSettings(activity: Activity, open: Runnable): View {
        val frame = object : android.widget.FrameLayout(activity) {
            override fun onInterceptTouchEvent(event: android.view.MotionEvent) = true
        }
        frame.setOnClickListener { open.run() }
        frame.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        frame.addView(ComposeHost(activity) {
            ShortcutAction(tr("Oniimai 설정", "Oniimai 设置"), modifier = Modifier.fillMaxSize()
                .semantics { contentDescription = tr("Oniimai 설정 · 끌어서 이동", "Oniimai 设置 · 拖动移动") }, onClick = open::run)
        }, android.widget.FrameLayout.LayoutParams(-1, -1))
        return frame
    }

    @JvmStatic fun home(activity: Activity, prefs: SharedPreferences, preview: Runnable, launch: Runnable): View = ComposeHost(activity) {
        var revision by remember { mutableIntStateOf(0) }
        key(revision) {
            Page(tr("Oniimai", "Oniimai")) {
                LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
                    item(key = "hero") {
                        // The large page title already says "Oniimai"; the hero describes what the module does.
                        HeroCard(tr("컨트롤러 모듈", "控制器模块"), tr("KanadeDX 전용 · 터치·버튼 입력, RGB 조명, 외부 화면", "KanadeDX 专用 · 触摸与按钮输入、RGB 灯光、外接屏幕"),
                            listOf("v${BuildConfig.VERSION_NAME}", "LSPosed API 102"))
                    }
                    item(key = "main") { Card {
                        SuperArrow(title = tr("KanadeDX 열기", "打开 KanadeDX"), summary = tr("게임 안에서 컨트롤러를 설정합니다", "在游戏内设置控制器"),
                            leftAction = { RowIcon(IconTint.blue, MiuixIcons.Useful.Play) }, onClick = launch::run)
                        SuperArrow(title = tr("대시보드 미리보기", "预览仪表盘"), summary = tr("위젯 배치와 크기를 편집합니다", "编辑小组件的布局与大小"),
                            leftAction = { RowIcon(IconTint.purple, MiuixIcons.Useful.Edit) }, onClick = preview::run)
                    } }
                    item(key = "app") { SectionTitle(tr("앱 설정", "应用设置")); Card {
                        SuperArrow(title = tr("언어", "语言"), rightText = UiLanguage.name(), leftAction = { RowIcon(IconTint.green, label = "文") }, onClick = {
                            UiLanguage.choose(activity, prefs, null) { revision++ }
                        })
                        SuperArrow(title = tr("라이선스 · 출처", "许可与来源"), summary = "GPL-3.0 · MPL-2.0",
                            leftAction = { RowIcon(IconTint.slate, MiuixIcons.Useful.Info) }, onClick = { LicenseUi.show(activity, null) })
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
    }

    @JvmStatic fun settings(session: GameSession, close: Runnable): AlertDialog = dialog(session.activity()) {
        var tab by remember { mutableIntStateOf(session.settingsTab()) }
        var groups by remember { mutableStateOf(session.nativeSettings(tab)) }
        val view = LocalView.current
        LaunchedEffect(tab) {
            groups = session.nativeSettings(tab)
            while (true) { delay(2000); if (view.isShown && view.hasWindowFocus()) groups = session.nativeSettings(tab) }
        }
        Page(tr("컨트롤러 설정", "控制器设置"), back = close::run) {
            TabRow(listOf(tr("연결", "连接"), tr("화면", "屏幕"), tr("버튼", "按钮"), "LED"), tab,
                modifier = Modifier.padding(horizontal = OniTokens.inset), height = OniTokens.target,
                onTabSelected = { session.settingsTab(it); tab = it })
            key(tab) {
                val list = rememberLazyListState(session.prefs().getInt("native_scroll_index_$tab", 0), session.prefs().getInt("native_scroll_offset_$tab", 0))
                DisposableEffect(tab) { onDispose { session.prefs().edit().putInt("native_scroll_index_$tab", list.firstVisibleItemIndex).putInt("native_scroll_offset_$tab", list.firstVisibleItemScrollOffset).apply() } }
                LazyColumn(state = list, modifier = Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
                    item(key = "hint") {
                        Caption(tr("자동 저장 · 설정 중에는 게임 입력 일시 정지", "自动保存 · 设置时暂停游戏输入"),
                            Modifier.padding(horizontal = OniTokens.inset))
                    }
                    itemsIndexed(groups, key = { index, _ -> "group-$index" }) { _, group ->
                        SettingsGroup(group) { groups = session.nativeSettings(tab) }
                    }
                    // App-level preferences sit together at the end of the first tab instead of bracketing the hardware groups.
                    if (tab == 0) item(key = "general") {
                        Column {
                            SectionTitle(tr("일반", "通用"))
                            Card {
                                SuperArrow(tr("언어", "语言"), rightText = UiLanguage.name(), leftAction = { RowIcon(IconTint.green, label = "文") }, onClick = session::chooseLanguage)
                                SuperArrow(title = tr("라이선스 · 출처", "许可与来源"), summary = "GPL-3.0 · MPL-2.0", leftAction = { RowIcon(IconTint.slate, MiuixIcons.Useful.Info) }, onClick = session::showLicenses)
                            }
                        }
                    }
                }
            }
        }
    }

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

    internal fun dialog(activity: Activity, content: @Composable () -> Unit): AlertDialog = AlertDialog.Builder(activity, android.R.style.Theme_Material_Light_NoActionBar)
        .create().apply { setView(ComposeHost(activity, content), 0, 0, 0, 0) }

    @JvmStatic fun showFullScreen(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.let { window ->
            GameSession.styleSettingsSystemBars(window)
            val dark = dialog.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val color = if (dark) AndroidColor.BLACK else GameUi.PAGE
            window.setBackgroundDrawable(ColorDrawable(color)); window.statusBarColor = color; window.navigationBarColor = color
            window.setLayout(-1, -1)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false)
                val light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
            } else if (dark) window.decorView.systemUiVisibility = 0
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @JvmStatic fun choice(activity: Activity, title: String, options: Array<String>, selected: Int, protect: Consumer<AlertDialog>?, action: IntConsumer): AlertDialog {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            Page(title, back = { dialog.dismiss() }) {
                LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), overscrollEffect = null) {
                    item { Card { options.forEachIndexed { index, option ->
                        // Catalog entries arrive as "title\ndescription"; show the second line as a summary.
                        val lines = option.split('\n', limit = 2)
                        OptionRow(lines[0], selected = index == selected, summary = lines.getOrNull(1),
                            onClick = { dialog.dismiss(); action.accept(index) })
                    } } }
                }
            }
        }
        protect?.accept(dialog); showFullScreen(dialog); return dialog
    }

    @JvmStatic fun number(activity: Activity, title: String, current: Int, min: Int, max: Int, protect: Consumer<AlertDialog>, action: IntConsumer) {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            var text by remember { mutableStateOf(current.toString()) }
            val number = text.toIntOrNull()
            val valid = number != null && number in min..max
            val digits = maxOf(min.toString().length, max.toString().length)
            val save: () -> Unit = { if (valid) { action.accept(number!!); dialog.dismiss() } }
            Page(title, back = { dialog.dismiss() }) {
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
        protect.accept(dialog); showFullScreen(dialog)
    }

    @JvmStatic fun brightness(activity: Activity, current: Int, protect: Consumer<AlertDialog>, action: IntConsumer) {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            var value by remember { mutableFloatStateOf(current.coerceIn(0, 100).toFloat()) }
            var sent by remember { mutableIntStateOf(current) }
            // Live preview, but only when the whole percentage changes: a drag emits a value per frame
            // and each accepted value is written to the LED controller.
            val send: (Int) -> Unit = { next -> if (next != sent) { sent = next; action.accept(next) } }
            Page(tr("LED 밝기", "LED 亮度"), back = { dialog.dismiss() }) {
                Card(Modifier.padding(OniTokens.inset), insideMargin = PaddingValues(OniTokens.inset)) {
                    Text("${value.toInt()}%", fontSize = OniTokens.displayMetric, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"))
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
        protect.accept(dialog); showFullScreen(dialog)
    }

    @JvmStatic fun setup(activity: Activity, prefs: SharedPreferences, protect: Consumer<AlertDialog>?, changed: Runnable?, finished: Runnable?): AlertDialog {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            var step by remember { mutableIntStateOf(0) }
            var language by remember { mutableStateOf(UiText.language()) }
            var external by remember { mutableStateOf(prefs.getBoolean("external_enabled", true)) }
            var clockwise by remember { mutableStateOf(prefs.getBoolean("external_clockwise", false)) }
            var auto by remember { mutableStateOf(prefs.getBoolean("auto_connect", true)) }
            var led by remember { mutableStateOf(prefs.getBoolean("led_enabled", true)) }
            var aime by remember { mutableStateOf(prefs.getBoolean("aime_enabled", true)) }
            key(language) {
                Page(if (step == 0) "Language / 语言" else if (step == 1) tr("모니터 방향", "显示器方向") else tr("컨트롤러 연결", "控制器连接"),
                    back = { if (step == 0) dialog.dismiss() else step-- }) {
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
                                        if (language != code) { language = code; UiText.language(code); prefs.edit().putString("ui_language", code).apply(); changed?.run() }
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
                        if(step < 2) step++ else {
                            prefs.edit().putBoolean("external_enabled", external).putBoolean("external_clockwise", clockwise).putBoolean("auto_connect", auto)
                                .putBoolean("led_enabled", led).putBoolean("aime_enabled", aime).putBoolean("setup_complete", true).apply()
                            changed?.run(); dialog.dismiss()
                        }
                    }
                }
            }
        }
        dialog.setCanceledOnTouchOutside(false); dialog.setOnDismissListener { finished?.run() }
        protect?.accept(dialog); showFullScreen(dialog); return dialog
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
/** Settings lists are long; slightly tighter rows (still above the 48dp target) keep more of a group on screen. */
private val DENSE_ROW = PaddingValues(horizontal = OniTokens.inset, vertical = OniTokens.gap)

@Composable
private fun SettingsGroup(group: NativeSettings.Group, refresh: () -> Unit) {
    val rows = group.rows
    val first = rows.indexOfFirst { it.toggle != null || it.action != null }
    Column {
        SectionTitle(group.title)
        if (first < 0) {
            Footnotes(rows.map { it.summary })
        } else {
            val lead = rows.take(first).map { it.summary }.filter { it.isNotBlank() }
            val rest = rows.drop(first)
            Card {
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
