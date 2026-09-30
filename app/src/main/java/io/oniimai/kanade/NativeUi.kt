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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import java.util.function.Consumer
import java.util.function.IntConsumer

/**
 * Entry points are callable from the small Java Activity and the injected Unity session.
 * This file owns dialogs, preferences and the game session; the screens themselves live in OniScreens.kt.
 */
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
            HomeScreen(UiLanguage.name(), onLaunch = launch::run, onPreview = preview::run,
                onLanguage = { UiLanguage.choose(activity, prefs, null) { revision++ } },
                onLicenses = { LicenseUi.show(activity, null) })
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
        SettingsScreen(tab, groups, UiLanguage.name(),
            onTab = { session.settingsTab(it); tab = it },
            refresh = { groups = session.nativeSettings(tab) },
            close = close::run, onLanguage = session::chooseLanguage, onLicenses = session::showLicenses,
            listState = { page ->
                val prefs = session.prefs()
                val list = rememberLazyListState(prefs.getInt("native_scroll_index_$page", 0), prefs.getInt("native_scroll_offset_$page", 0))
                DisposableEffect(page) { onDispose { prefs.edit().putInt("native_scroll_index_$page", list.firstVisibleItemIndex).putInt("native_scroll_offset_$page", list.firstVisibleItemScrollOffset).apply() } }
                list
            })
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
            ChoiceScreen(title, options, selected, onBack = { dialog.dismiss() }, onPick = { dialog.dismiss(); action.accept(it) })
        }
        protect?.accept(dialog); showFullScreen(dialog); return dialog
    }

    @JvmStatic fun number(activity: Activity, title: String, current: Int, min: Int, max: Int, protect: Consumer<AlertDialog>, action: IntConsumer) {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            NumberScreen(title, current, min, max, onBack = { dialog.dismiss() }, onSave = { action.accept(it); dialog.dismiss() })
        }
        protect.accept(dialog); showFullScreen(dialog)
    }

    @JvmStatic fun brightness(activity: Activity, current: Int, protect: Consumer<AlertDialog>, action: IntConsumer) {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) { BrightnessScreen(current, onBack = { dialog.dismiss() }, onChange = action::accept) }
        protect.accept(dialog); showFullScreen(dialog)
    }

    @JvmStatic fun setup(activity: Activity, prefs: SharedPreferences, protect: Consumer<AlertDialog>?, changed: Runnable?, finished: Runnable?): AlertDialog {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            val initial = remember { SetupChoices(prefs.getBoolean("external_enabled", true), prefs.getBoolean("external_clockwise", false),
                prefs.getBoolean("auto_connect", true), prefs.getBoolean("led_enabled", true), prefs.getBoolean("aime_enabled", true)) }
            SetupScreen(UiText.language(), initial,
                onLanguage = { code -> UiText.language(code); prefs.edit().putString("ui_language", code).apply(); changed?.run() },
                onCancel = { dialog.dismiss() },
                onFinish = { c ->
                    prefs.edit().putBoolean("external_enabled", c.external).putBoolean("external_clockwise", c.clockwise).putBoolean("auto_connect", c.auto)
                        .putBoolean("led_enabled", c.led).putBoolean("aime_enabled", c.aime).putBoolean("setup_complete", true).apply()
                    changed?.run(); dialog.dismiss()
                })
        }
        dialog.setCanceledOnTouchOutside(false); dialog.setOnDismissListener { finished?.run() }
        protect?.accept(dialog); showFullScreen(dialog); return dialog
    }
}
