// Modified 2026-09-29 (UI refinement pass, see CHANGES-UI.md). Original: KanadeDX-Oniimai 1.1.0-rc5 @ 1c9c518b.
package io.oniimai.kanade

import android.app.Activity
import android.graphics.Bitmap
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Android glue for the phone dashboard; the widget layouts live in OniScreens.kt. */
internal object NativeDashboard {
    @JvmStatic fun pageColor(activity: Activity): Int = if (GameUi.night(activity)) 0xff000000.toInt() else GameUi.PAGE
    @JvmStatic fun header(activity: Activity, editing: Boolean, preview: Boolean, demo: Boolean, action: Runnable): View = ComposeHost(activity) {
        DashboardHeader(editing, preview, demo, action::run)
    }
    @JvmStatic fun footer(activity: Activity, labels: Array<String>, actions: Array<Runnable>, primaryFirst: Boolean): View = ComposeHost(activity) {
        DashboardFooter(labels.toList(), actions.map { it::run }, primaryFirst)
    }
    @JvmStatic fun widget(activity: Activity, type: String, host: DashboardHost, state: WidgetState): View = ComposeHost(activity) {
        DashboardWidgetCard(type, DashboardView.title(type), state) { modifier ->
            val colors = MiuixTheme.colorScheme
            // Button segments carry white numerals, so they need a solid fill in both themes;
            // the translucent summary colour washed out to grey-on-white in dark mode.
            val ring = if (isSystemInDarkTheme()) Color(0xFF4A4A4A) else Color(GameUi.TAB)
            AndroidView(factory = { SensorBoard(host.activity(), host).withoutFooter().gameTheme().compact(true) }, modifier = modifier,
                update = { it.palette(colors.primary.toArgb(), colors.secondaryContainer.toArgb(), colors.surface.toArgb(), colors.onSurface.toArgb(), ring.toArgb()) })
        }
    }
    @JvmStatic fun update(state: WidgetState, frame: JSONObject, stale: Boolean, cover: Bitmap?, host: DashboardHost, type: String) {
        state.frame = frame; state.stale = stale
        if (state.coverSource !== cover) { state.coverSource = cover; state.cover = cover?.asImageBitmap() }
        if (type == "connection") { state.input = host.connectionDescription(); state.led = host.ledDescription(); state.output = host.displayDescription() }
        if (type == "sensors") { val d = host.diagnostic(); state.sensors = "T ${java.lang.Long.bitCount(d[1])}/34   B ${Integer.bitCount(d[0].toInt() and 255)}/8   P1 ${if (d[0] and 256L != 0L) "ON" else "OFF"}" }
        if (type == "clock") { state.minute = System.currentTimeMillis() / 60000; state.is24Hour = android.text.format.DateFormat.is24HourFormat(host.activity()) }
    }
}
