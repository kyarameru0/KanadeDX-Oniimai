package io.oniimai.kanade

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
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
import androidx.compose.ui.unit.sp
import androidx.compose.animation.togetherWith
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

@Composable internal fun HomeScreen(language: String, onLaunch: () -> Unit, onPreview: () -> Unit, onLanguage: () -> Unit, onLicenses: () -> Unit,
                                   onAbout: (() -> Unit)? = null) {
    Page(str(Msg.HOME_TITLE)) {
        LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
            item(key = "hero") {
                // The large page title already says "Oniimai"; the hero describes what the module does.
                // Launching the game is the reason to open this app, so it is the hero's button rather than a list row.
                HeroCard(str(Msg.HOME_HERO_TITLE), str(Msg.HOME_HERO_SUBTITLE),
                    listOf("v${BuildConfig.VERSION_NAME}", "LSPosed API 102"), action = str(Msg.HOME_LAUNCH), onAction = onLaunch)
            }
            item(key = "main") { Card {
                SuperArrow(title = str(Msg.HOME_PREVIEW), summary = str(Msg.HOME_PREVIEW_SUMMARY),
                    leftAction = { RowIcon(IconTint.purple, MiuixIcons.Useful.Edit) }, onClick = onPreview)
            } }
            item(key = "app") { SectionTitle(str(Msg.HOME_APP_SETTINGS)); Card {
                SuperArrow(title = str(Msg.COMMON_LANGUAGE), rightText = language, leftAction = { RowIcon(IconTint.green, label = I18n.LANGUAGE_GLYPH) }, onClick = onLanguage)
                SuperArrow(title = str(Msg.LICENSE_TITLE), summary = "GPL-3.0 · MPL-2.0",
                    leftAction = { RowIcon(IconTint.slate, MiuixIcons.Useful.Info) }, onClick = onLicenses)
                if (onAbout != null) SuperArrow(title = str(Msg.ABOUT_TITLE), rightText = "v${BuildConfig.VERSION_NAME}", leftAction = { AboutRowIcon() }, onClick = onAbout)
            } }
            item(key = "guide") { SectionTitle(str(Msg.HOME_GUIDE)); Card(insideMargin = PaddingValues(vertical = OniTokens.space)) {
                GuideStep(1, str(Msg.HOME_GUIDE_STEP1), str(Msg.HOME_GUIDE_STEP1_DETAIL))
                GuideStep(2, str(Msg.HOME_GUIDE_STEP2), str(Msg.HOME_GUIDE_STEP2_DETAIL))
                GuideStep(3, str(Msg.HOME_GUIDE_STEP3), str(Msg.HOME_GUIDE_STEP3_DETAIL))
            } }
            item(key = "footer") {
                Box(Modifier.fillMaxWidth().padding(OniTokens.inset), contentAlignment = Alignment.Center) { Caption("KanadeDX 1.60 / 1.65 · Android 9+") }
            }
        }
    }
}

@Composable internal fun SettingsScreen(tab: Int, groups: List<NativeSettings.Group>, language: String, onTab: (Int) -> Unit, refresh: () -> Unit,
                                        close: () -> Unit, onLanguage: () -> Unit, onLicenses: () -> Unit,
                                        listState: @Composable (Int) -> LazyListState = { rememberLazyListState() }, general: Boolean = true,
                                        onAbout: (() -> Unit)? = null) {
    Page(str(Msg.SETTINGS_TITLE), back = close) {
        TabRow(listOf(str(Msg.SETTINGS_TAB_CONNECTION), str(Msg.COMMON_DISPLAY), str(Msg.SETTINGS_TAB_BUTTONS), "LED"), tab,
            modifier = Modifier.padding(horizontal = OniTokens.inset), height = OniTokens.target, onTabSelected = onTab)
        // The tab on screen always reads the newest groups (states and switches change while it is open); a
        // tab sliding out keeps the groups it last showed, so it does not show the next tab's while it leaves.
        val shown = remember { HashMap<Int, List<NativeSettings.Group>>() }
        shown[tab] = groups
        androidx.compose.animation.AnimatedContent(targetState = tab, label = "tab", transitionSpec = {
            // The new tab comes in from the side it was chosen on, a short way, over a cross-fade.
            val side = if (targetState > initialState) 1 else -1
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(280)) { side * it / 5 } +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(280))) togetherWith
            (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(220)) { -side * it / 5 } +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(180)))
        }) { page ->
            LazyColumn(state = listState(page), modifier = Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
                item(key = "hint") {
                    Caption(str(Msg.SETTINGS_AUTOSAVE_HINT),
                        Modifier.padding(horizontal = OniTokens.inset))
                }
                itemsIndexed(if (page == tab) groups else shown[page] ?: emptyList(), key = { index, _ -> "group-$index" }) { _, group -> SettingsGroup(group, refresh) }
                // App-level preferences sit together at the end of the first tab instead of bracketing the hardware groups.
                if (page == 0 && general) item(key = "general") {
                    Column {
                        SectionTitle(str(Msg.SETTINGS_GENERAL))
                        Card {
                            SuperArrow(str(Msg.COMMON_LANGUAGE), rightText = language, leftAction = { RowIcon(IconTint.green, label = I18n.LANGUAGE_GLYPH) }, onClick = onLanguage)
                            SuperArrow(title = str(Msg.LICENSE_TITLE), summary = "GPL-3.0 · MPL-2.0", leftAction = { RowIcon(IconTint.slate, MiuixIcons.Useful.Info) }, onClick = onLicenses)
                            if (onAbout != null) SuperArrow(title = str(Msg.ABOUT_TITLE), rightText = "v${BuildConfig.VERSION_NAME}", leftAction = { AboutRowIcon() }, onClick = onAbout)
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
            Caption(if (valid || text.isEmpty()) str(Msg.NUMBER_RANGE, min, max) else str(Msg.NUMBER_ENTER_BETWEEN, min, max),
                Modifier.padding(horizontal = OniTokens.space), color = if (valid || text.isEmpty()) MiuixTheme.colorScheme.onSurfaceVariantSummary else ERROR_TEXT)
            Spacer(Modifier.height(OniTokens.space))
            Action(str(Msg.COMMON_SAVE), true, Modifier.fillMaxWidth(), enabled = valid) { save() }
        }
    }
}

@Composable internal fun BrightnessScreen(current: Int, onBack: () -> Unit, onChange: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(current.coerceIn(0, 100).toFloat()) }
    var sent by remember { mutableIntStateOf(current) }
    // Live preview, but only when the whole percentage changes: a drag emits a value per frame
    // and each accepted value is written to the LED controller.
    val send: (Int) -> Unit = { next -> if (next != sent) { sent = next; onChange(next) } }
    Page(str(Msg.SETTINGS_LED_BRIGHTNESS), back = onBack) {
        Card(Modifier.padding(OniTokens.inset), insideMargin = PaddingValues(OniTokens.inset)) {
            Text("${value.toInt()}%", fontSize = OniTokens.displayMetric, fontWeight = FontWeight.SemiBold, style = TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(OniTokens.gap))
            Slider(value = value, onValueChange = { value = it; send(it.toInt()) }, valueRange = 0f..100f,
                onValueChangeFinished = { send(value.toInt()) },
                modifier = Modifier.heightIn(min = OniTokens.target).semantics { contentDescription = str(Msg.SETTINGS_LED_BRIGHTNESS) })
            Spacer(Modifier.height(OniTokens.space))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                listOf(25, 50, 75, 100).forEach { preset ->
                    Action("$preset%", preset == value.toInt(), Modifier.weight(1f)) { value = preset.toFloat(); send(preset) }
                }
            }
        }
        Caption(str(Msg.BRIGHTNESS_NOTE), Modifier.padding(horizontal = OniTokens.titleInset))
    }
}

/** Values chosen in first-run setup, written by the caller only when the user finishes. */
internal class SetupChoices(var external: Boolean, var clockwise: Boolean, var auto: Boolean, var led: Boolean, var aime: Boolean)

/**
 * First-run setup. [signals] reports what the app really knows about the controller (read about 30 times a
 * second); every connection or test result shown here comes from it, never from an animation finishing.
 * [onSearch] lets the app look for the controller from the connection step on, [onRetry] asks it to look
 * again after a refusal or failure, and [onScreen] receives what setup shows, for the controller's own screen
 * and lights.
 * Opened again after setup was finished ([firstRun] false), the welcome screen has a back arrow that closes it.
 * The phone's back goes the way the page's back arrows do; it never skips the steps to close setup.
 */
@Composable internal fun SetupScreen(initialLanguage: String, initial: SetupChoices, onLanguage: (String) -> Unit, onCancel: () -> Unit,
                                     onFinish: (SetupChoices) -> Unit, signals: () -> SetupSignals = { SetupSignals() },
                                     onSearch: () -> Unit = {}, onRetry: () -> Unit = {}, firstRun: Boolean = true,
                                     onScreen: (SetupView) -> Unit = {},
                                     advanced: (tab: Int) -> List<NativeSettings.Group> = { emptyList() },
                                     stored: () -> SetupChoices? = { null }) {
    // Welcome; then the language (so every page reads in it), connection, the built-in screen's direction (so
    // the input check and lighting read upright on the controller), input check and lighting; then done. Steps
    // are SetupLights.WELCOME to DONE; the numbered pages run from FIRST to LAST.
    var step by remember { mutableIntStateOf(SetupLights.WELCOME) }
    var language by remember { mutableStateOf(initialLanguage) }
    // From the connection step on, the app may look for the controller and ask for USB permission.
    LaunchedEffect(step) { if (step >= SetupLights.CONNECT) onSearch() }
    var external by remember { mutableStateOf(initial.external) }
    var clockwise by remember { mutableStateOf(initial.clockwise) }
    var auto by remember { mutableStateOf(initial.auto) }
    var led by remember { mutableStateOf(initial.led) }
    var aime by remember { mutableStateOf(initial.aime) }
    // Detailed settings save at once; a choice they change is taken over here, so finishing cannot undo it.
    // [stored] reports the values as the detailed settings show them (the live rotation preview, the running
    // LED and Aime state), and the comparison restarts whenever they open, so every change made there, even one
    // back to what was saved before, is a change from what they showed and wins over setup's unsaved choice.
    var lastStored by remember { mutableStateOf(stored()) }
    // Detailed settings: the Settings page itself, on the tab that goes with the step, until its back arrow.
    var advancedTab by remember { mutableStateOf<Int?>(null) }
    // The tab the detailed settings page showed last, for it to keep while it slides out.
    var lastAdvanced by remember { mutableIntStateOf(0) }
    val adoptStored = {
        val now = stored(); val before = lastStored
        if (now != null && before != null) {
            if (now.external != before.external) external = now.external
            if (now.clockwise != before.clockwise) clockwise = now.clockwise
            if (now.auto != before.auto) auto = now.auto
            if (now.led != before.led) led = now.led
            if (now.aime != before.aime) aime = now.aime
        }
        lastStored = now
    }
    var live by remember { mutableStateOf(signals()) }
    // Paused while setup's window has no focus (the USB permission dialog on top, the game in front); read
    // again on the first frame after it returns.
    val active = motionActive()
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        while (true) withFrameNanos { if (it - last >= 33_000_000L) { last = it; live = signals() } }
    }
    // Buttons and touch areas that have really answered, so progress stays when they are let go.
    var checkedButtons by remember { mutableIntStateOf(0) }
    var checkedTouches by remember { mutableLongStateOf(0L) }
    LaunchedEffect(live) {
        if (live.live && live.buttonsLinked) checkedButtons = checkedButtons or (live.buttons and 0xFF)
        if (live.live && live.touchLinked) checkedTouches = checkedTouches or live.touches
    }
    var inputView by remember { mutableStateOf(InputView.BUTTONS) }
    // All eight buttons answered: move on to the touch areas by itself.
    LaunchedEffect(checkedButtons) { if (checkedButtons == 0xFF) inputView = InputView.TOUCH }
    var focusAime by remember(step) { mutableStateOf(false) }
    val scene = when (step) {
        SetupLights.WELCOME -> MotionScene.INTRO; SetupLights.SCREEN -> MotionScene.MONITOR; SetupLights.LANGUAGE -> MotionScene.LANGUAGE
        SetupLights.CONNECT -> MotionScene.CONNECT; SetupLights.INPUT -> MotionScene.INPUT; SetupLights.LIGHTING -> MotionScene.LIGHTING
        else -> MotionScene.DONE
    }
    val labels = MotionLabels(str(Msg.SETUP_CALLOUT_USB), str(Msg.SETUP_CALLOUT_LED), str(Msg.SETUP_CALLOUT_AIME), str(Msg.SETUP_CALLOUT_SCREEN),
        str(Msg.SETUP_DONE_READY))
    val link = if (live.live) live.link else LinkState.WAITING
    // What is missing from a partial link, in the words the Settings status already uses.
    val partialStatus = when {
        live.buttonsLinked && !live.touchLinked -> Msg.INPUT_STATUS_BUTTONS_ONLY
        live.touchLinked && !live.buttonsLinked -> Msg.INPUT_STATUS_TOUCH_ONLY
        else -> Msg.SETUP_CONNECT_STATUS_PARTIAL
    }
    val inputLinked = live.live && (if (inputView == InputView.BUTTONS) live.buttonsLinked else live.touchLinked)
    val page = when (step) {
        SetupLights.CONNECT -> StepText(str(Msg.SETTINGS_CONNECTION_GROUP),
            str(when (link) { LinkState.WAITING -> Msg.SETUP_CONNECT_TITLE_WAITING; LinkState.PERMISSION -> Msg.SETUP_CONNECT_TITLE_PERMISSION
                LinkState.CONNECTED -> Msg.SETUP_CONNECT_TITLE_CONNECTED; LinkState.PARTIAL -> Msg.SETUP_CONNECT_TITLE_PARTIAL
                LinkState.FAILED -> Msg.SETUP_CONNECT_TITLE_FAILED }),
            str(when (link) { LinkState.WAITING -> Msg.SETUP_CONNECT_BODY_WAITING; LinkState.PERMISSION -> Msg.SETUP_CONNECT_BODY_PERMISSION
                LinkState.CONNECTED -> Msg.SETUP_CONNECT_BODY_CONNECTED; LinkState.PARTIAL -> Msg.SETUP_CONNECT_BODY_PARTIAL
                LinkState.FAILED -> Msg.SETUP_CONNECT_BODY_FAILED }),
            // Before setup is finished the app does not look for USB devices, so say when it will.
            if (!live.live) StepStatus(str(if (auto) Msg.SETUP_CONNECT_STATUS_AUTO else Msg.SETUP_CONNECT_STATUS_MANUAL), StepTone.QUIET)
            else when (link) {
                LinkState.WAITING -> StepStatus(str(Msg.SETUP_CONNECT_STATUS_WAITING), StepTone.WAIT)
                LinkState.PERMISSION -> StepStatus(str(Msg.SETUP_CONNECT_STATUS_PERMISSION), StepTone.WAIT)
                // Everything expected is open; name only what that is.
                LinkState.CONNECTED -> StepStatus(str(when {
                    live.touchLinked && !live.buttonsLinked -> Msg.INPUT_STATUS_TOUCH
                    live.buttonsLinked && !live.touchLinked -> Msg.INPUT_STATUS_BUTTONS
                    else -> Msg.SETUP_CONNECT_STATUS_CONNECTED
                }), StepTone.OK)
                LinkState.PARTIAL -> StepStatus(str(partialStatus), StepTone.WARN)
                LinkState.FAILED -> StepStatus(str(Msg.SETUP_CONNECT_STATUS_FAILED), StepTone.WARN)
            })
        SetupLights.INPUT -> StepText(str(Msg.SETUP_INPUT_EYEBROW),
            str(if (inputView == InputView.BUTTONS) Msg.SETUP_INPUT_BUTTONS_HEADING else Msg.SETUP_INPUT_TOUCH_HEADING),
            str(if (inputView == InputView.BUTTONS) Msg.SETUP_INPUT_BUTTONS_BODY else Msg.SETUP_INPUT_TOUCH_BODY),
            if (!inputLinked && link == LinkState.PARTIAL) StepStatus(str(partialStatus), StepTone.WARN)
            else if (!inputLinked) StepStatus(str(Msg.SETUP_INPUT_UNAVAILABLE), StepTone.QUIET)
            else if (inputView == InputView.BUTTONS) StepStatus(str(Msg.SETUP_INPUT_BUTTONS_COUNT, checkedButtons.countOneBits()), StepTone.INFO)
            else StepStatus(str(Msg.SETUP_INPUT_TOUCH_COUNT, checkedTouches.countOneBits()), StepTone.INFO))
        SetupLights.LIGHTING -> StepText(str(Msg.SETUP_LIGHTING_EYEBROW), str(Msg.SETUP_LIGHTING_HEADING), str(Msg.SETUP_LIGHTING_BODY),
            // The controller's lights follow this step whenever they can be reached; otherwise say why not.
            if (!led) StepStatus(str(Msg.SETUP_LIGHTING_OFF), StepTone.QUIET)
            else if (live.live && live.ledLinked) StepStatus(str(Msg.SETUP_LIGHTING_LIVE), StepTone.OK)
            else StepStatus(str(Msg.SETUP_LIGHTING_UNLINKED), StepTone.WAIT))
        SetupLights.LANGUAGE -> StepText(I18n.LANGUAGE_TITLE, str(Msg.SETUP_LANGUAGE_HEADING), str(Msg.SETUP_LANGUAGE_BODY),
            StepStatus(str(Msg.SETUP_LANGUAGE_HINT), StepTone.QUIET))
        else -> StepText(str(Msg.SETUP_MONITOR_TITLE), str(Msg.SETUP_MONITOR_HEADING), str(Msg.SETUP_MONITOR_BODY),
            // The screen is read directly (a display, not a USB port), whether or not the controller is connected.
            // "Showing" only when the game really is on it; otherwise setup's own page is, with "this side up".
            when {
                !live.screenAttached -> StepStatus(str(Msg.SETUP_MONITOR_STATUS_OFF), StepTone.QUIET)
                !external -> StepStatus(str(Msg.SETUP_MONITOR_STATUS_DISABLED), StepTone.QUIET)
                live.screenShowing -> StepStatus(str(Msg.SETUP_MONITOR_STATUS_ON), StepTone.OK)
                else -> StepStatus(str(Msg.SETUP_MONITOR_HINT), StepTone.INFO)
            })
    }
    // What the controller's own screen and lights show: this page's words, or the welcome and done screens'.
    val shown = when (step) {
        SetupLights.WELCOME -> StepText("", str(Msg.SETUP_WELCOME), str(Msg.SETUP_WELCOME_BODY), StepStatus(str(Msg.SETUP_SCREEN_PHONE), StepTone.QUIET))
        SetupLights.DONE -> StepText("", str(Msg.SETUP_FINISH), str(Msg.SETUP_DONE_BODY), when (link) {
            LinkState.CONNECTED -> StepStatus(str(Msg.SETUP_DONE_CONNECTED), StepTone.OK)
            LinkState.PARTIAL -> StepStatus(str(Msg.SETUP_DONE_PARTIAL), StepTone.WARN)
            else -> StepStatus(str(Msg.SETUP_DONE_LATER), StepTone.QUIET)
        })
        else -> page
    }
    val view = SetupView(step, clockwise, external, led, live, inputView, checkedButtons, checkedTouches,
        shown.eyebrow, shown.heading, shown.body, shown.status.text, shown.status.tone.ordinal, rotationHeld = advancedTab != null)
    LaunchedEffect(view) { onScreen(view) }
    // The phone's back, as the back arrow on screen: out of the detailed settings, one step back, from done to
    // lighting, and from the first step to the welcome screen. Only the welcome screen's back closes setup, and
    // only where its arrow does (opened again from Settings); on first run it stays.
    PageBack {
        when {
            advancedTab != null -> { advancedTab = null; adoptStored() }
            step == SetupLights.DONE -> step = SetupLights.LAST
            step > SetupLights.WELCOME -> step--
            !firstRun -> onCancel()
        }
    }
    key(language) {
      androidx.compose.animation.Crossfade(targetState = if (step == SetupLights.WELCOME) 0 else if (step == SetupLights.DONE) 2 else 1,
          animationSpec = androidx.compose.animation.core.tween(450), label = "stage") { screen ->
        if (screen == 0) WelcomeScreen(labels, view, onStart = { step = SetupLights.FIRST }, onClose = if (firstRun) null else onCancel)
        else if (screen == 2) DoneScreen(SetupChoices(external, clockwise, auto, led, aime), link, labels, view, onBack = { step = SetupLights.LAST },
            onStart = { onFinish(SetupChoices(external, clockwise, auto, led, aime)) })
        // Detailed settings slide in over the step from the right, and back out to it, like any other page.
        else androidx.compose.animation.AnimatedContent(targetState = advancedTab != null, label = "advanced", transitionSpec = {
            val enter = androidx.compose.animation.core.tween<androidx.compose.ui.unit.IntOffset>(320, easing = androidx.compose.animation.core.FastOutSlowInEasing)
            if (targetState) (androidx.compose.animation.slideInHorizontally(enter) { it } togetherWith
                (androidx.compose.animation.slideOutHorizontally(enter) { -it / 4 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(320))))
                .apply { targetContentZIndex = 1f }
            else ((androidx.compose.animation.slideInHorizontally(enter) { -it / 4 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(320))) togetherWith
                androidx.compose.animation.slideOutHorizontally(enter) { it }).apply { targetContentZIndex = -1f }
        }) { open ->
        if (open) {
            val tab = advancedTab ?: lastAdvanced
            var groups by remember(tab) { mutableStateOf(advanced(tab)) }
            val refresh = { groups = advanced(tab); adoptStored() }
            // Read again every two seconds, as the Settings panel does, so connection states stay current.
            LaunchedEffect(tab) { while (true) { kotlinx.coroutines.delay(2000); refresh() } }
            SettingsScreen(tab, groups, "", onTab = { advancedTab = it; lastAdvanced = it }, refresh = refresh,
                close = { advancedTab = null; adoptStored() }, onLanguage = {}, onLicenses = {}, general = false)
        }
        else {
            // The main button never claims a result: waiting for permission disables it, a failure offers a retry.
            val primary: Pair<String, (() -> Unit)?> = when {
                step == SetupLights.CONNECT && live.live && link == LinkState.PERMISSION -> str(Msg.SETUP_CONNECT_PERMISSION_WAIT) to null
                step == SetupLights.CONNECT && live.live && link == LinkState.FAILED -> str(Msg.SETUP_CONNECT_RETRY) to onRetry
                step == SetupLights.CONNECT && live.live && link == LinkState.WAITING -> str(Msg.SETUP_CONNECT_LATER) to { step += 1 }
                else -> str(Msg.SETUP_NEXT) to { step += 1 }
            }
            val secondary: Pair<String, () -> Unit>? = when {
                step == SetupLights.CONNECT && live.live && link == LinkState.FAILED -> str(Msg.SETUP_CONNECT_LATER) to { step += 1 }
                step == SetupLights.CONNECT && live.live && link == LinkState.PARTIAL -> str(Msg.SETUP_CONNECT_RETRY) to onRetry
                else -> null
            }
            SetupStepPage(step, page, primary, secondary, onBack = { step-- },
                // One motion scene for every step, so the controller stays put and only the guidance changes.
                hero = { SetupMotion(scene, live, labels, external, led, clockwise, height = 340.dp, inputView = inputView,
                    checkedButtons = checkedButtons, checkedTouches = checkedTouches, focusAime = focusAime, fadeEdges = true, view = view) }) {
                when (step) {
                    SetupLights.CONNECT -> Column {
                        Card { SuperSwitch(title = str(Msg.SETTINGS_AUTO_CONNECT), checked = auto, onCheckedChange = { auto = it }) }
                        SetupAdvanced { lastStored = stored(); advancedTab = 0; lastAdvanced = 0 }
                    }
                    SetupLights.INPUT -> Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space, Alignment.CenterHorizontally)) {
                            for ((view, title) in listOf(InputView.BUTTONS to Msg.SETUP_INPUT_BUTTONS_TAB, InputView.TOUCH to Msg.SETUP_INPUT_TOUCH_TAB))
                                HeaderAction(str(title), primary = inputView == view) { inputView = view }
                        }
                        SetupAdvanced { lastStored = stored(); advancedTab = 2; lastAdvanced = 2 }
                    }
                    SetupLights.LIGHTING -> Column {
                        Card {
                            SuperSwitch(title = str(Msg.SETTINGS_LED_GROUP), checked = led, onCheckedChange = { led = it; focusAime = false })
                            SuperSwitch(title = str(Msg.SETTINGS_AIME_TOGGLE), checked = aime, onCheckedChange = { aime = it; focusAime = true })
                        }
                        SetupAdvanced { lastStored = stored(); advancedTab = 3; lastAdvanced = 3 }
                    }
                    SetupLights.LANGUAGE -> Card {
                        I18n.NAMES.forEachIndexed { i, title ->
                            val code = I18n.CODES[i]
                            OptionRow(title, selected = language == code, onClick = {
                                if (language != code) { language = code; onLanguage(code) }
                            })
                        }
                    }
                    else -> {
                        Card { SuperSwitch(title = str(Msg.SETTINGS_DISPLAY_TOGGLE), summary = str(Msg.SETTINGS_DISPLAY_TOGGLE_SUMMARY), checked = external,
                            onCheckedChange = { external = it }) }
                        // Rotation only matters with output on; keep it visible but disabled otherwise.
                        SectionTitle(str(Msg.SETTINGS_DISPLAY_ROTATION))
                        Card {
                            OptionRow(str(Msg.SETUP_ROTATION_CW), selected = clockwise, enabled = external, onClick = { clockwise = true })
                            OptionRow(str(Msg.SETUP_ROTATION_CCW), selected = !clockwise, enabled = external, onClick = { clockwise = false })
                        }
                        SetupAdvanced { lastStored = stored(); advancedTab = 1; lastAdvanced = 1 }
                    }
                }
            }
        }
        }
      }
    }
}

/**
 * The way from a setup step to detailed settings: the Settings page itself (connection, screen, buttons,
 * LED), opened on the tab that goes with the step. Its changes save at once, as in Settings.
 */
@Composable private fun SetupAdvanced(onOpen: () -> Unit) {
    Card(Modifier.padding(top = OniTokens.gap)) {
        SuperArrow(title = str(Msg.SETUP_ADVANCED), summary = str(Msg.SETUP_ADVANCED_SUMMARY), onClick = onOpen)
    }
}

private class StepText(val eyebrow: String, val heading: String, val body: String, val status: StepStatus)
private class StepStatus(val text: String, val tone: StepTone)
private enum class StepTone { QUIET, INFO, WAIT, OK, WARN }

/**
 * First screen of setup, after ColorOS: a greeting that cycles through every UI language over soft drifting
 * colour, the controller appearing above it, and one round arrow button to begin.
 */
@Composable private fun WelcomeScreen(labels: MotionLabels, view: SetupView?, onStart: () -> Unit, onClose: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    val clock = backdropClock()
    // The current language first, then the others; changes every 2.6 s on the backdrop's clock, so it holds
    // still with the backdrop. Only a change of greeting recomposes; the moving backdrop just redraws.
    val greetings = remember { I18n.CODES.indices.map { I18n.textIn(I18n.CODES[(I18n.index() + it) % I18n.CODES.size], Msg.SETUP_WELCOME) } }
    val shown by remember { derivedStateOf { ((clock.longValue / 2_600_000_000L) % greetings.size).toInt() } }
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawAurora(clock.longValue / 1e9f, dark) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = OniTokens.section),
            horizontalAlignment = Alignment.CenterHorizontally) {
            // Opened again from Settings: a way back out before starting over.
            if (onClose != null) Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { BackButton(onClose) }
            SetupBody(Modifier.weight(1f)) { art ->
                SetupMotion(MotionScene.INTRO, SetupSignals(), labels, screens = false, leds = false, clockwise = false,
                    Modifier.widthIn(max = art * 1.8f), height = art, intro = true, view = view)
                Spacer(Modifier.height(OniTokens.section))
                androidx.compose.animation.AnimatedContent(targetState = shown, label = "greeting", transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(600)) +
                        androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(600)) { it / 3 }) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(350)) +
                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(350)) { -it / 3 })
                }) { index ->
                    // One line, shrinking only as far as the width needs, so large text never breaks the word.
                    Text(greetings[index], fontSize = 40.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
                        autoSize = TextAutoSize.StepBased(20.sp, 40.sp))
                }
                Spacer(Modifier.height(OniTokens.space))
                Text(str(Msg.SETUP_WELCOME_BODY), fontSize = OniTokens.label, textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Spacer(Modifier.height(OniTokens.space))
            Button(onClick = onStart, cornerRadius = 32.dp, minWidth = 64.dp, minHeight = 64.dp,
                colors = ButtonDefaults.buttonColorsPrimary(), insideMargin = PaddingValues(0.dp),
                modifier = Modifier.size(64.dp).semantics { contentDescription = str(Msg.SETUP_WELCOME_START) }) {
                androidx.compose.foundation.Canvas(Modifier.size(26.dp)) {
                    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round,
                        join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    val arrow = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width * 0.18f, size.height / 2); lineTo(size.width * 0.82f, size.height / 2)
                        moveTo(size.width * 0.55f, size.height * 0.24f); lineTo(size.width * 0.82f, size.height / 2); lineTo(size.width * 0.55f, size.height * 0.76f)
                    }
                    drawPath(arrow, Color.White, style = stroke)
                }
            }
            Spacer(Modifier.height(OniTokens.space))
            Caption(str(Msg.SETUP_WELCOME_START))
            Spacer(Modifier.height(OniTokens.section))
        }
    }
}

/**
 * Last screen of setup: the framing returns to the whole controller, guides fade away and one quiet light
 * runs round the ring. "Connected" shows only when the app really saw the controller during setup.
 */
@Composable private fun DoneScreen(choices: SetupChoices, link: LinkState, labels: MotionLabels, view: SetupView?, onBack: () -> Unit, onStart: () -> Unit) {
    val dark = isSystemInDarkTheme()
    val clock = backdropClock()
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawAurora(clock.longValue / 1e9f, dark) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { BackButton(onBack) }
            SetupBody(Modifier.weight(1f).padding(horizontal = OniTokens.section)) { art ->
                // As wide as the drawing needs, so the "Ready" tag stays by the controller in landscape.
                SetupMotion(MotionScene.DONE, SetupSignals(), labels, screens = choices.external, leds = choices.led, clockwise = choices.clockwise,
                    Modifier.widthIn(max = art * 1.8f), height = art, view = view)
                Spacer(Modifier.height(OniTokens.section))
                Text(str(Msg.SETUP_FINISH), fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(OniTokens.space))
                Text(str(Msg.SETUP_DONE_BODY), fontSize = OniTokens.label, textAlign = TextAlign.Center,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Spacer(Modifier.height(OniTokens.space))
                Caption(str(when (link) {
                    LinkState.CONNECTED -> Msg.SETUP_DONE_CONNECTED
                    LinkState.PARTIAL -> Msg.SETUP_DONE_PARTIAL
                    else -> Msg.SETUP_DONE_LATER
                }))
            }
            Action(str(Msg.SETUP_WELCOME_START), true, Modifier.fillMaxWidth().widthIn(max = OniTokens.width)
                .padding(horizontal = OniTokens.inset, vertical = OniTokens.gap), onClick = onStart)
        }
    }
}

/**
 * The middle of the welcome and done screens: centred when it fits, scrolling when it does not (a short or
 * landscape window, large text), with the controller drawn at up to 300 dp but no more than half the height.
 * The action below it stays on screen either way.
 */
@Composable private fun SetupBody(modifier: Modifier, content: @Composable ColumnScope.(art: androidx.compose.ui.unit.Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth()) {
        val art = (maxHeight * 0.5f).coerceIn(120.dp, 300.dp)
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { content(art) }
    }
}

/**
 * The welcome and done screens' drifting backdrop, in nanoseconds, under the same rules as the setup motion:
 * it holds still while setup's window has no focus or system animations are off, including when they are
 * turned off while setup stays open.
 * Read it while drawing, so the backdrop redraws without recomposing.
 */
@Composable private fun backdropClock(): androidx.compose.runtime.MutableLongState {
    val still = motionStill()
    val active = motionActive()
    val nanos = remember { mutableLongStateOf(0L) }
    LaunchedEffect(active, still) {
        if (!active || still) return@LaunchedEffect
        val offset = withFrameNanos { it } - nanos.longValue
        while (true) withFrameNanos { nanos.longValue = it - offset }
    }
    return nanos
}

/**
 * One setup step, laid out like a device-pairing sheet: back and progress at the top, the step's name,
 * heading and explanation, the motion large in the middle, the step's choices, then a status line over a
 * pill-shaped main button. The title and the button stay put while the motion plays.
 */
@Composable private fun SetupStepPage(step: Int, text: StepText, primary: Pair<String, (() -> Unit)?>, secondary: Pair<String, () -> Unit>?,
                                      onBack: () -> Unit, hero: @Composable () -> Unit, choices: @Composable ColumnScope.() -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = MiuixTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) { drawShowcase(dark) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().widthIn(max = OniTokens.width).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                BackButton(onBack)
                Spacer(Modifier.weight(1f))
                Caption(str(Msg.SETTINGS_SETUP) + " · ${step + 1} / 5")
                Spacer(Modifier.width(OniTokens.space))
                StepIndicator(step, 5, Modifier.width(64.dp))
                Spacer(Modifier.width(OniTokens.section))
            }
            LazyColumn(Modifier.weight(1f).widthIn(max = OniTokens.width).miuiScroll(), contentPadding = PaddingValues(horizontal = OniTokens.inset),
                verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
                item(key = "text") {
                    // New words rise in as the old fade, when the step or what it says changes.
                    androidx.compose.animation.AnimatedContent(targetState = listOf(text.eyebrow, text.heading, text.body), label = "words", transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(260, delayMillis = 60)) +
                            androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(300)) { it / 6 }) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(140))
                    }) { (eyebrow, heading, body) ->
                        Column(Modifier.fillMaxWidth().padding(top = OniTokens.space), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                            Text(eyebrow, fontSize = OniTokens.caption, fontWeight = FontWeight.SemiBold, color = colors.primary)
                            Text(heading, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = OniTokens.inset))
                            Text(body, fontSize = OniTokens.label, textAlign = TextAlign.Center, color = colors.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = OniTokens.inset))
                        }
                    }
                }
                item(key = "controller") { hero() }
                item(key = "choices-$step") {
                    Column(Modifier.animateItem(fadeInSpec = androidx.compose.animation.core.tween(260, delayMillis = 80), placementSpec = null,
                        fadeOutSpec = androidx.compose.animation.core.tween(120))) { choices() }
                }
            }
            Column(Modifier.fillMaxWidth().widthIn(max = OniTokens.width).padding(horizontal = OniTokens.inset, vertical = OniTokens.gap),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = OniTokens.inset)) {
                    val dot = when (text.status.tone) {
                        StepTone.OK, StepTone.INFO, StepTone.WAIT -> colors.primary
                        StepTone.WARN -> Color(0xFFE8A33A)
                        StepTone.QUIET -> colors.onSurfaceVariantSummary
                    }
                    Box(Modifier.size(7.dp).clip(CircleShape).background(dot))
                    Spacer(Modifier.width(OniTokens.space))
                    Text(text.status.text, fontSize = OniTokens.caption, textAlign = TextAlign.Center,
                        color = if (text.status.tone == StepTone.QUIET) colors.onSurfaceVariantSummary else colors.onSurface)
                }
                if (secondary != null) TextButton(text = secondary.first, onClick = secondary.second, modifier = Modifier.fillMaxWidth(),
                    minHeight = 40.dp, colors = ButtonDefaults.textButtonColors(color = Color.Transparent, textColor = colors.primary))
                else Spacer(Modifier.height(OniTokens.space))
                TextButton(text = primary.first, onClick = primary.second ?: {}, enabled = primary.second != null, modifier = Modifier.fillMaxWidth(),
                    minHeight = 54.dp, cornerRadius = 27.dp, colors = ButtonDefaults.textButtonColorsPrimary())
            }
        }
    }
}

/**
 * What the About page shows. Game-side values are null where they are not known: the module's own launcher
 * has no running game, so its page leaves out the module state and the controller.
 */
internal data class AboutInfo(val version: String, val channel: String, val deviceName: String, val device: String, val android: String,
                              val osBuild: String, val gameVersion: String?, val module: String?, val firmware: String?)

/** The app's icon (drawable/ic_launcher) as a rounded tile: the white ring and its light centre on blue. */
@Composable internal fun AppLogo(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier.clip(RoundedCornerShape(26))) {
        val s = size.minDimension / 108f
        drawRect(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF4A93FF), Color(0xFF007AFF), Color(0xFF0060E0)),
            androidx.compose.ui.geometry.Offset.Zero, androidx.compose.ui.geometry.Offset(size.width, size.height)))
        drawCircle(Color.White, 32.5f * s, center, style = androidx.compose.ui.graphics.drawscope.Stroke(11f * s))
        drawCircle(Color(0xFFB8D8FF), 13f * s, center)
    }
}

/**
 * About, in the manner of HyperOS and HyperCeiler: a soft colour wash behind the app's icon, its name in a
 * gradient and the version; then the phone, the game and the module, diagnostics, and the source and licences.
 * [info] is read again every second, so a firmware check started here shows its answer.
 */
@Composable internal fun AboutScreen(info: () -> AboutInfo, onBack: () -> Unit, onSource: () -> Unit, onLicenses: () -> Unit,
                                     onFirmware: (() -> Unit)? = null, onDiagnostics: (() -> Unit)? = null) {
    val dark = isSystemInDarkTheme()
    val colors = MiuixTheme.colorScheme
    var shown by remember { mutableStateOf(info()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); shown = info() } }
    val about = shown
    Box(Modifier.fillMaxSize().background(colors.background)) {
        // Kept in its own offscreen layer: the large soft gradients are painted once, not again on every scroll frame.
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(620.dp)
            .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }) { drawAboutWash(dark, colors.background) }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().widthIn(max = OniTokens.width).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) { BackButton(onBack) }
            Spacer(Modifier.height(64.dp))
            AppLogo(Modifier.size(96.dp))
            Spacer(Modifier.height(18.dp))
            // The module's full name on one line, as large as the width allows, one gradient across it.
            androidx.compose.foundation.text.BasicText("Oniimai for KanadeDX", Modifier.fillMaxWidth().padding(horizontal = OniTokens.section),
                style = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        if (dark) listOf(Color(0xFFFF8CC6), Color(0xFFB596FF), Color(0xFF7FB2FF)) else listOf(Color(0xFFC2417F), Color(0xFF7A3FD8), Color(0xFF3A6FE8)))),
                maxLines = 1, autoSize = TextAutoSize.StepBased(22.sp, 40.sp))
            Spacer(Modifier.height(8.dp))
            Text("${about.version} | ${about.channel}", fontSize = OniTokens.label, color = colors.onSurfaceVariantSummary,
                style = TextStyle(fontFeatureSettings = "tnum"))
            Spacer(Modifier.height(88.dp))
            Column(Modifier.fillMaxWidth().widthIn(max = OniTokens.width).padding(horizontal = OniTokens.inset),
                verticalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(about.deviceName, fontSize = 26.sp, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    AboutEntry(about.device, str(Msg.ABOUT_DEVICE))
                    AboutEntry(about.android, str(Msg.ABOUT_ANDROID))
                    AboutEntry(about.osBuild, str(Msg.ABOUT_BUILD))
                }
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(horizontal = 20.dp, vertical = 18.dp)) {
                    Text("KanadeDX", fontSize = 26.sp, color = colors.onSurface)
                    Spacer(Modifier.height(10.dp))
                    about.gameVersion?.let { AboutEntry(it, str(Msg.ABOUT_GAME_VERSION)) }
                    about.module?.let { AboutEntry(it, str(Msg.ABOUT_MODULE)) }
                    AboutEntry("LSPosed API 102", str(Msg.ABOUT_MODULE_NAME))
                }
                if (onFirmware != null || onDiagnostics != null) {
                    Card {
                        if (onFirmware != null) SuperArrow(title = str(Msg.SETTINGS_FIRMWARE), summary = about.firmware, onClick = onFirmware)
                        if (onDiagnostics != null) SuperArrow(title = str(Msg.SETTINGS_COPY_DIAGNOSTICS), summary = about.module, onClick = onDiagnostics)
                    }
                }
                SectionTitle(str(Msg.ABOUT_OTHERS))
                Card {
                    SuperArrow(title = str(Msg.ABOUT_SOURCE), summary = str(Msg.ABOUT_SOURCE_SUMMARY), onClick = onSource)
                    SuperArrow(title = str(Msg.LICENSE_TITLE), summary = "GPL-3.0 · MPL-2.0", onClick = onLicenses)
                }
                Box(Modifier.fillMaxWidth().padding(vertical = OniTokens.section), contentAlignment = Alignment.Center) {
                    Caption("Oniimai for KanadeDX · GPL-3.0-only")
                }
            }
        }
    }
}

/** The app's icon at the size of a settings row's icon. */
@Composable private fun AboutRowIcon() {
    AppLogo(Modifier.padding(end = OniTokens.inset).size(OniTokens.rowIcon))
}

/** One fact on an About card: the value, and under it what it is. */
@Composable private fun AboutEntry(value: String, label: String) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Text(value, fontSize = OniTokens.label, color = MiuixTheme.colorScheme.onSurface)
        Text(label, fontSize = OniTokens.caption, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

/** The colour wash behind the About page's top: pink to lavender to blue, settling into the page colour. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawAboutWash(dark: Boolean, page: Color) {
    val w = size.width; val h = size.height
    fun glow(color: Color, x: Float, y: Float, r: Float, alpha: Float) = drawCircle(
        androidx.compose.ui.graphics.Brush.radialGradient(listOf(color.copy(alpha = alpha), color.copy(alpha = 0f)),
            androidx.compose.ui.geometry.Offset(x, y), r), r, androidx.compose.ui.geometry.Offset(x, y))
    if (dark) {
        glow(Color(0xFF7A2E6A), w * 0.05f, h * 0.18f, w * 0.95f, 0.55f)
        glow(Color(0xFF4A3A9E), w * 0.55f, h * 0.45f, w * 0.85f, 0.45f)
        glow(Color(0xFF24408F), w * 1.0f, h * 0.30f, w * 0.75f, 0.45f)
    } else {
        glow(Color(0xFFF8BDDC), w * 0.0f, h * 0.20f, w * 1.0f, 0.75f)
        glow(Color(0xFFD9CCFF), w * 0.55f, h * 0.42f, w * 0.9f, 0.7f)
        glow(Color(0xFFC6D6FF), w * 1.05f, h * 0.55f, w * 0.8f, 0.7f)
    }
    // Settle into the page so the cards sit on plain background.
    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(0.6f to page.copy(alpha = 0f), 1f to page, startY = 0f, endY = h))
}

@Composable internal fun LicenseScreen(version: String, sourceUrl: String, files: List<String>, onBack: () -> Unit, onSource: () -> Unit, onFile: (String) -> Unit) {
    Page(str(Msg.LICENSE_TITLE), back = onBack) {
        LazyColumn(Modifier.fillMaxSize().miuiScroll(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap), overscrollEffect = null) {
            item { Card(insideMargin = PaddingValues(OniTokens.inset)) {
                Text("Oniimai $version · GPL-3.0-only")
                Spacer(Modifier.height(OniTokens.gap))
                Text(str(Msg.LICENSE_ABOUT))
                Spacer(Modifier.height(OniTokens.gap))
                Text(str(Msg.LICENSE_TERMS))
            } }
            item { Card {
                SuperArrow(title = str(Msg.LICENSE_SOURCE), summary = sourceUrl, onClick = onSource)
            } }
            item { Card(insideMargin = PaddingValues(OniTokens.inset)) {
                Text(str(Msg.LICENSE_OFFLINE))
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
            if (failed) item { Caption(color = ERROR_TEXT, text = str(Msg.LICENSE_LOAD_FAILED)) }
            else if (paragraphs == null) item { Caption(str(Msg.COMMON_LOADING), Modifier.padding(horizontal = OniTokens.inset)) }
            else items(paragraphs) { paragraph -> SelectionContainer { Text(paragraph, modifier = Modifier.fillMaxWidth()) } }
        }
    }
}

/* ---------------- Dashboard ---------------- */

@Composable internal fun DashboardHeader(editing: Boolean, preview: Boolean, demo: Boolean, action: () -> Unit) {
    Column(Modifier.background(MiuixTheme.colorScheme.background)) {
        PageHeader(if (editing) str(Msg.DASHBOARD_EDIT_TITLE) else str(Msg.DASHBOARD_TITLE), action = {
            HeaderAction(if (editing) str(Msg.COMMON_SAVE) else str(Msg.COMMON_EDIT), editing, onClick = action)
        })
        if (editing || preview) Caption(when {
            editing -> str(Msg.DASHBOARD_EDIT_HINT)
            demo -> str(Msg.DASHBOARD_PREVIEW_HINT)
            else -> str(Msg.DASHBOARD_LAYOUT_HINT)
        }, Modifier.padding(start = OniTokens.inset, end = OniTokens.inset, bottom = OniTokens.gap))
    }
}

/**
 * The dashboard's actions as a slim bar of pill buttons, so the widgets keep most of the screen. The same space
 * above and below as between the widgets (the dashboard adds none of its own), so the buttons sit centred.
 * Each label keeps to one line where it can: the buttons share the width equally when every label fits its
 * share, and by what each label needs when one does not. When they cannot all fit side by side (a narrow
 * screen, large text) they stack, one per line, as the platform's own dialogs do. Buttons side by side always
 * have one height, with their labels centred.
 */
@Composable internal fun DashboardFooter(labels: List<String>, actions: List<() -> Unit>, primaryFirst: Boolean) {
    Layout(content = { labels.forEachIndexed { index, label -> FooterButton(label, primaryFirst && index == 0, actions[index]) } },
        modifier = Modifier.background(MiuixTheme.colorScheme.background).fillMaxWidth().padding(horizontal = OniTokens.inset, vertical = 10.dp)
    ) { buttons, constraints ->
        val width = constraints.maxWidth
        if (buttons.isEmpty()) return@Layout layout(width, 0) {}
        val gap = OniTokens.space.roundToPx()
        val room = width - gap * (buttons.size - 1)
        // What each button needs to show its label on one line.
        val need = buttons.map { it.maxIntrinsicWidth(Constraints.Infinity) }
        val share = room / buttons.size
        val widths = when {
            need.all { it <= share } -> List(buttons.size) { share + if (it < room - share * buttons.size) 1 else 0 }
            need.sum() <= room -> {
                val extra = room - need.sum()
                need.mapIndexed { i, n -> n + extra / buttons.size + if (i < extra % buttons.size) 1 else 0 }
            }
            else -> null
        }
        if (widths != null) {
            val height = buttons.indices.maxOf { buttons[it].minIntrinsicHeight(widths[it]) }
            val placed = buttons.mapIndexed { i, button -> button.measure(Constraints.fixed(widths[i], height)) }
            layout(width, height) { var x = 0; placed.forEach { it.place(x, 0); x += it.width + gap } }
        } else {
            val placed = buttons.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
            layout(width, placed.sumOf { it.height } + gap * (placed.size - 1)) { var y = 0; placed.forEach { it.place(0, y); y += it.height + gap } }
        }
    }
}

/** One of the dashboard's pill buttons: Miuix's text button, with its label centred when it has to wrap. */
@Composable private fun FooterButton(label: String, primary: Boolean, onClick: () -> Unit) {
    val colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors()
    Button(onClick = onClick, minHeight = 42.dp, cornerRadius = 21.dp, insideMargin = PaddingValues(horizontal = OniTokens.inset, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(colors.color, colors.disabledColor)) {
        Text(label, color = colors.textColor, style = MiuixTheme.textStyles.button, textAlign = TextAlign.Center)
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
    var inputTone by mutableIntStateOf(NativeSettings.NONE)
    var ledTone by mutableIntStateOf(NativeSettings.NONE)
    var outputTone by mutableIntStateOf(NativeSettings.NONE)
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
                SmallMetric(str(Msg.WIDGET_SCORE_DX), number("dx"))
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
            listOf(Triple(str(Msg.WIDGET_CONNECTION_INPUT), state.input, state.inputTone), Triple("LED", state.led, state.ledTone), Triple(str(Msg.COMMON_DISPLAY), state.output, state.outputTone)).forEach { (name, value, tone) ->
                // Narrow tiles show each status's headline only; the wide tile has room for its second line.
                val lines = value.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(OniTokens.space)) {
                    Box(Modifier.padding(top = 5.dp).size(OniTokens.dot).clip(CircleShape).background(connectionTone(value, tone)))
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
            val day = SimpleDateFormat(str(Msg.WIDGET_CLOCK_DATE_PATTERN), uiLocale()).format(date)
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
        "List" -> str(Msg.WIDGET_SONG_SELECTING)
        "Result" -> str(Msg.WIDGET_SONG_RESULT)
        else -> str(Msg.WIDGET_SONG_WAITING)
    } }
    val artist = data.optString("artist").ifBlank { str(Msg.WIDGET_SONG_HINT) }
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
        Image(cover, str(Msg.WIDGET_SONG_ARTWORK), modifier.clip(shape), contentScale = ContentScale.Crop)
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
private fun connectionTone(value: String, tone: Int): Color {
    // The game session reports real link state; the text is read only for hosts that do not (the launcher preview).
    when (tone) { NativeSettings.OK -> return IconTint.green; NativeSettings.WAIT -> return IconTint.orange; NativeSettings.INFO -> return Color(GameUi.MUTED) }
    val head = value.lineSequence().firstOrNull().orEmpty().lowercase(I18n.locale())
    fun has(id: Int) = head.contains(str(id).lowercase(I18n.locale()))
    return when {
        head.contains("off") || has(Msg.WIDGET_CONNECTION_MATCH_PREVIEW) || has(Msg.SETTINGS_PHONE_GROUP) -> Color(GameUi.MUTED)
        has(Msg.WIDGET_CONNECTION_MATCH_CONNECTED) || has(Msg.WIDGET_CONNECTION_MATCH_LINKED) || head.contains("16:9") -> IconTint.green
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

private fun uiLocale() = I18n.locale()
