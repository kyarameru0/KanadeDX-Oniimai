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
    /** The parent owns drag gestures; Compose supplies the themed, accessible action. */
    @JvmStatic fun floatingSettings(activity: Activity, open: Runnable): View {
        val frame = object : android.widget.FrameLayout(activity) {
            override fun onInterceptTouchEvent(event: android.view.MotionEvent) = true
        }
        frame.setOnClickListener { open.run() }
        frame.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        frame.addView(ComposeHost(activity) {
            ShortcutAction(str(Msg.OVERLAY_SETTINGS), modifier = Modifier
                .semantics { contentDescription = str(Msg.OVERLAY_SETTINGS_DESCRIPTION) }, onClick = open::run)
        }, android.widget.FrameLayout.LayoutParams(-2, -2))
        return frame
    }

    /** A wrap-content in-app notice; does not create a window or interrupt the game installer. */
    @JvmStatic fun inlineNotice(activity: Activity, text: String, dismiss: Runnable): View = ComposeHost(activity) {
        InlineNotice(text, dismiss::run)
    }

    @JvmStatic fun home(activity: Activity, prefs: SharedPreferences, preview: Runnable, launch: Runnable): View = ComposeHost(activity) {
        var revision by remember { mutableIntStateOf(0) }
        key(revision) {
            HomeScreen(UiLanguage.name(), onLaunch = launch::run, onPreview = preview::run,
                onLanguage = { UiLanguage.choose(activity, prefs, null) { revision++ } },
                onLicenses = { LicenseUi.show(activity, null) },
                onAbout = { val game = installedGame(activity); about(activity, { aboutInfo(activity, game, null, null) }, null, null, null) })
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
            close = close::run, onLanguage = session::chooseLanguage, onLicenses = session::showLicenses, onAbout = session::showAbout,
            listState = { page ->
                val prefs = session.prefs()
                val list = rememberLazyListState(prefs.getInt("native_scroll_index_$page", 0), prefs.getInt("native_scroll_offset_$page", 0))
                DisposableEffect(page) { onDispose { prefs.edit().putInt("native_scroll_index_$page", list.firstVisibleItemIndex).putInt("native_scroll_offset_$page", list.firstVisibleItemScrollOffset).apply() } }
                list
            })
    }

    internal fun dialog(activity: Activity, content: @Composable () -> Unit): AlertDialog = PageDialog(activity).apply {
        val backs = backs
        setView(ComposeHost(activity) { CompositionLocalProvider(LocalPageBacks provides backs, content = content) }, 0, 0, 0, 0)
    }

    /** A full-screen page holding a native view (the dashboard preview). */
    @JvmStatic fun page(activity: Activity, view: View): AlertDialog = PageDialog(activity).apply { setView(view, 0, 0, 0, 0) }


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

    /** Setup's choices as saved; detailed settings change these directly. */
    private fun stored(prefs: SharedPreferences) = SetupChoices(prefs.getBoolean("external_enabled", true), prefs.getBoolean("external_clockwise", false),
        prefs.getBoolean("auto_connect", true), prefs.getBoolean("led_enabled", true), prefs.getBoolean("aime_enabled", true))

    /** Setup's own screen for the controller's display, following [state]. */
    @JvmStatic fun externalSetup(activity: Activity, state: ExternalSetupState): View =
        ComposeHost(activity) { key(state.language) { ExternalSetupScreen(state.view) } }

    /** The About page as a full-screen dialog; [info] is read again while it is open. */
    @JvmStatic fun about(activity: Activity, info: java.util.function.Supplier<AboutInfo>, protect: Consumer<AlertDialog>?,
                         firmware: Runnable?, diagnostics: Runnable?) {
        lateinit var panel: AlertDialog
        panel = dialog(activity) {
            AboutScreen(info::get, onBack = { panel.dismiss() },
                onSource = {
                    try { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(LicenseText.SOURCE_URL))) }
                    catch (_: android.content.ActivityNotFoundException) { android.widget.Toast.makeText(activity, str(Msg.LICENSE_NO_BROWSER), android.widget.Toast.LENGTH_SHORT).show() }
                },
                onLicenses = { LicenseUi.show(activity, protect) },
                onFirmware = firmware?.let { f -> { f.run() } }, onDiagnostics = diagnostics?.let { d -> { d.run() } })
        }
        protect?.accept(panel); showFullScreen(panel)
    }

    /** The phone's own facts, read once: the device name is a settings query, and About reads its values every second. */
    @Volatile private var phone: AboutInfo? = null

    /** The phone, this module and, where known, the game, for the About page. */
    @JvmStatic fun aboutInfo(activity: Activity, gameVersion: String?, module: String?, firmware: String?): AboutInfo {
        val base = phone ?: run {
            val named = try { android.provider.Settings.Global.getString(activity.contentResolver, android.provider.Settings.Global.DEVICE_NAME) } catch (_: RuntimeException) { null }
            val device = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL).filter { !it.isNullOrBlank() }.distinct().joinToString(" ")
            AboutInfo("${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", BuildConfig.BUILD_TYPE,
                named?.takeIf { it.isNotBlank() } ?: device, device, android.os.Build.VERSION.RELEASE ?: "—",
                android.os.Build.VERSION.INCREMENTAL ?: android.os.Build.DISPLAY ?: "—", null, null, null).also { phone = it }
        }
        return base.copy(gameVersion = gameVersion, module = module, firmware = firmware)
    }

    /** The installed game's version, as the launcher sees it; null when it is not installed or cannot be seen. */
    private fun installedGame(activity: Activity): String? = listOf("app.KanadeDX", "app.KanadeDX.oniimai").firstNotNullOfOrNull { name ->
        try { activity.packageManager.getPackageInfo(name, 0).versionName } catch (_: Exception) { null }
    }

    /** What setup shows about the controller, read from the session; nothing here opens or asks for anything. */
    private fun setupSignals(probe: SetupProbe): SetupSignals {
        val link = probe.setupLink()
        if (link < 0) return SetupSignals(screenAttached = probe.external(), screenShowing = probe.externalActive())
        val d = probe.diagnostic()
        val open = probe.setupInputs()
        return SetupSignals(live = true, link = when (link) { 1 -> LinkState.PERMISSION; 2 -> LinkState.CONNECTED; 3 -> LinkState.FAILED; 4 -> LinkState.PARTIAL
                else -> LinkState.WAITING },
            buttons = (d[0] and 0xFFL).toInt(), touches = d[1], buttonsLinked = open and 2 != 0, touchLinked = open and 1 != 0,
            ledLinked = probe.ledLinked(), screenAttached = probe.external(), screenShowing = probe.externalActive())
    }

    @JvmStatic fun brightness(activity: Activity, current: Int, protect: Consumer<AlertDialog>, action: IntConsumer) {
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) { BrightnessScreen(current, onBack = { dialog.dismiss() }, onChange = action::accept) }
        protect.accept(dialog); showFullScreen(dialog)
    }

    @JvmStatic fun setup(activity: Activity, prefs: SharedPreferences, probe: SetupProbe?, protect: Consumer<AlertDialog>?, changed: Runnable?, finished: Runnable?): AlertDialog {
        // Opened again from Settings after setup was finished: the welcome screen can be closed with its back arrow.
        val firstRun = !prefs.getBoolean("setup_complete", false)
        lateinit var dialog: AlertDialog
        dialog = dialog(activity) {
            val initial = remember { stored(prefs) }
            SetupScreen(I18n.language(), initial,
                onLanguage = { code -> UiLanguage.save(prefs, code); changed?.run() },
                onCancel = { dialog.dismiss() },
                onFinish = { c ->
                    prefs.edit().putBoolean("external_enabled", c.external).putBoolean("external_clockwise", c.clockwise).putBoolean("auto_connect", c.auto)
                        .putBoolean("led_enabled", c.led).putBoolean("aime_enabled", c.aime).putBoolean("setup_complete", true).apply()
                    changed?.run(); dialog.dismiss()
                },
                signals = { probe?.let(::setupSignals) ?: SetupSignals() },
                onSearch = { probe?.setupSearch() }, onRetry = { probe?.setupRetry() },
                firstRun = firstRun,
                onScreen = { probe?.setupView(it) },
                advanced = { tab -> probe?.setupSettings(tab) ?: emptyList() },
                stored = { probe?.setupChoices() ?: stored(prefs) })
        }
        dialog.setCanceledOnTouchOutside(false); dialog.setOnDismissListener { finished?.run() }
        protect?.accept(dialog); showFullScreen(dialog); return dialog
    }
}

/** What setup's own screen on the controller's display shows; set by DisplayOutput on the UI thread. */
internal class ExternalSetupState {
    var view by mutableStateOf<SetupView?>(null)
    var language by mutableStateOf("")
}

/**
 * A full-screen page over the game. The game's activity turns hardware acceleration off, and windows opened
 * from it inherit that, so each page asks for it itself; otherwise every page would be drawn in software.
 * The page comes in from the right and leaves to the right as a whole window, animated by the system (the
 * platform's Animation.Translucent: a slide with a fade, following the system's animation scale). Moving the
 * window rather than its content leaves nothing behind on screen.
 */
internal class PageDialog(owner: Activity) : AlertDialog(owner, android.R.style.Theme_Material_Light_NoActionBar) {
    /** The page's own answers to the phone's back (see [PageBack]). */
    val backs = PageBacks()
    init {
        window?.addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED)
        window?.setWindowAnimations(android.R.style.Animation_Translucent)
    }
    /**
     * The phone's back, by key or gesture: the platform calls this either way (with back callbacks on, as the
     * dialog's default callback). A page that answers it stays open; otherwise back closes it as before.
     */
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onBackPressed() { if (!backs.press()) super.onBackPressed() }
}
