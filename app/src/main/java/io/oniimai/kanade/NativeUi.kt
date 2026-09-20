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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.extra.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
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
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
                    item { Card {
                        SuperArrow(title = tr("KanadeDX 열기", "打开 KanadeDX"), summary = tr("컨트롤러 설정은 게임 안에서 사용할 수 있습니다", "可在游戏内设置控制器"), onClick = launch::run)
                        SuperArrow(title = tr("대시보드 미리보기", "预览仪表盘"), summary = tr("위젯 배치와 크기를 편집합니다", "编辑小组件的布局与大小"), onClick = preview::run)
                    } }
                    item { SectionTitle(tr("앱 설정", "应用设置")); Card {
                        SuperArrow(title = tr("언어", "语言"), rightText = UiLanguage.name(), onClick = {
                            UiLanguage.choose(activity, prefs, null) { revision++ }
                        })
                        SuperArrow(title = tr("라이선스 · 출처", "许可与来源"), summary = "GPL-3.0 · MPL-2.0", onClick = { LicenseUi.show(activity, null) })
                    } }
                    item { SectionTitle(tr("사용 안내", "使用说明")); Card(insideMargin = PaddingValues(OniTokens.inset)) {
                        Text(tr("LSPosed 모듈을 활성화한 뒤 KanadeDX를 적용 대상으로 선택하세요.", "请启用 LSPosed 模块，并选择 KanadeDX 作用域。"))
                        Spacer(Modifier.height(OniTokens.gap))
                        Caption(tr("게임 안 Onii 설정에서 USB 연결, 외부 화면과 LED를 조절합니다. 미리보기에서는 USB에 연결하지 않습니다.", "在游戏内 Onii 设置中调整 USB 连接、外接屏幕和 LED。预览不会连接 USB。"))
                    } }
                    item { Caption("Oniimai ${BuildConfig.VERSION_NAME} · LSPosed API 102\nKanadeDX 1.60 / 1.65 · Android 9+", Modifier.padding(OniTokens.inset)) }
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
                LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
                    item { Caption(tr("변경사항은 자동 저장됩니다 · 설정 중에는 게임 입력이 멈춥니다", "更改自动保存 · 设置时暂停游戏输入")) }
                    if (tab == 0) item { Card { SuperArrow(tr("언어", "语言"), rightText = UiLanguage.name(), onClick = session::chooseLanguage) } }
                    itemsIndexed(groups, key = { index, _ -> "group-$index" }) { _, group ->
                        Column { SectionTitle(group.title); Card {
                            group.rows.forEach { row ->
                                when {
                                    row.toggle != null -> SuperSwitch(title = row.title, summary = row.summary.takeIf { it.isNotEmpty() }, checked = row.checked,
                                        onCheckedChange = { row.toggle.accept(it); groups = session.nativeSettings(tab) })
                                    row.action != null -> SuperArrow(title = row.title, summary = row.summary.takeIf { it.isNotEmpty() },
                                        onClick = { row.action.run(); groups = session.nativeSettings(tab) })
                                    else -> Caption(row.summary, Modifier.padding(OniTokens.inset))
                                }
                            }
                        } }
                    }
                    if (tab == 0) item { Card { SuperArrow(title = tr("라이선스 · 출처", "许可与来源"), summary = "GPL-3.0 · MPL-2.0", onClick = session::showLicenses) } }
                }
            }
        }
    }

    @Composable internal fun Page(title: String, back: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
        Box(Modifier.fillMaxSize().background(MiuixTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = OniTokens.width).fillMaxSize()) { PageHeader(title, back); content() }
        }
    }

    internal fun dialog(activity: Activity, content: @Composable () -> Unit): AlertDialog = AlertDialog.Builder(activity, android.R.style.Theme_Material_Light_NoActionBar)
        .create().apply { setView(ComposeHost(activity, content), 0, 0, 0, 0) }

    @JvmStatic fun showFullScreen(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.let { window ->
            GameSession.styleSettingsSystemBars(window)
            val dark = dialog.context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            val color = if (dark) AndroidColor.BLACK else 0xfff7f7f7.toInt()
            window.setBackgroundDrawable(ColorDrawable(color)); window.statusBarColor = color; window.navigationBarColor = color
            window.setLayout(-1, -1)
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                window.setDecorFitsSystemWindows(false)
                window.insetsController?.setSystemBarsAppearance(if (dark) 0 else 24, 24)
            } else if (dark) window.decorView.systemUiVisibility = 0
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    @JvmStatic fun choice(activity: Activity, title: String, options: Array<String>, selected: Int, protect: Consumer<AlertDialog>?, action: IntConsumer): AlertDialog {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            Page(title, back = { dialog.dismiss() }) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(OniTokens.inset)) {
                    item { Card { options.forEachIndexed { index, option ->
                        SuperArrow(title = option, rightText = if (index == selected) tr("선택됨", "已选择") else null,
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
            Page(title, back = { dialog.dismiss() }) {
                Column(Modifier.padding(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.inset)) {
                    TextField(value = text, onValueChange = { if (it.length <= 4) text = it }, label = "$min–$max", modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Caption(tr("범위: $min–$max", "范围：$min–$max"))
                    Action(tr("저장", "保存"), true, enabled = number != null && number in min..max) { action.accept(number!!); dialog.dismiss() }
                }
            }
        }
        protect.accept(dialog); showFullScreen(dialog)
    }

    @JvmStatic fun brightness(activity: Activity, current: Int, protect: Consumer<AlertDialog>, action: IntConsumer) {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            var value by remember { mutableFloatStateOf(current.toFloat()) }
            Page(tr("LED 밝기", "LED 亮度"), back = { dialog.dismiss() }) {
                Card(Modifier.padding(OniTokens.inset), insideMargin = PaddingValues(OniTokens.inset)) {
                    Text("${value.toInt()}%", fontSize = OniTokens.displayMetric)
                    Slider(value = value, onValueChange = { value = it; action.accept(it.toInt()) }, valueRange = 0f..100f,
                        modifier = Modifier.heightIn(min = OniTokens.target).semantics { contentDescription = tr("LED 밝기", "LED 亮度") })
                }
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
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.inset)) {
                        item { Caption(tr("초기 설정", "初始设置") + " · ${step+1} / 3") }
                        item { Card {
                            when (step) {
                                0 -> arrayOf("한국어", "简体中文").forEachIndexed { i, title ->
                                    val code = if (i == 0) "ko" else "zh-Hans"
                                    SuperArrow(title, rightText = if (language == code) tr("선택됨", "已选择") else null, onClick = {
                                        language = code; UiText.language(code); prefs.edit().putString("ui_language", code).apply(); changed?.run()
                                    })
                                }
                                1 -> {
                                    SuperSwitch(title = tr("외부 화면으로 게임 출력", "向外接屏幕输出游戏"), summary = tr("모니터 연결 시 자동 출력", "连接显示器时自动输出"), checked = external, onCheckedChange = { external = it })
                                    SuperArrow(tr("90° · 시계 방향", "90° · 顺时针"), rightText = if (clockwise) tr("선택됨", "已选择") else null, onClick = { clockwise = true })
                                    SuperArrow(tr("270° · 반대 방향", "270° · 逆时针"), rightText = if (!clockwise) tr("선택됨", "已选择") else null, onClick = { clockwise = false })
                                }
                                else -> {
                                    SuperSwitch(title = tr("자동 연결", "自动连接"), checked = auto, onCheckedChange = { auto = it })
                                    SuperSwitch(title = tr("게임 LED 연동", "游戏 LED 联动"), checked = led, onCheckedChange = { led = it })
                                    SuperSwitch(title = tr("Aime 카드 인식", "读取 Aime 卡"), checked = aime, onCheckedChange = { aime = it })
                                }
                            }
                        } }
                        item { Caption(when(step) {
                            0 -> tr("언어는 설정에서 언제든 변경할 수 있습니다.", "随时可在设置中更改语言。")
                            1 -> tr("가로 16:9 모니터에 게임을 90° 회전해 채웁니다. 폰에는 위젯 대시보드가 표시됩니다.", "将游戏旋转 90° 填满横向 16:9 显示器。手机显示小组件仪表盘。")
                            else -> tr("장치 이름으로 Touch·IO4·LED·NFC 포트를 찾습니다. USB 연결 권한을 허용해 주세요.", "按名称查找 Touch、IO4、LED 和 NFC 端口。请授予 USB 连接权限。")
                        }) }
                    }
                    Action(if(step == 2) tr("설정 완료", "完成设置") else tr("다음", "下一步"), true, Modifier.fillMaxWidth().padding(OniTokens.inset)) {
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
