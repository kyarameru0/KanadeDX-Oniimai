// Modified 2026-09-29 (UI refinement pass, see CHANGES-UI.md). Original: KanadeDX-Oniimai 1.1.0-rc5 @ 1c9c518b.
package io.oniimai.kanade

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.function.Consumer

/** Informational dialogs share the existing theme, back behavior and input guard. */
internal object LicenseUi {
    private fun tr(ko: String, zh: String) = GameUi.tr(ko, zh)

    @JvmStatic fun show(activity: Activity, protect: Consumer<AlertDialog>?) {
        lateinit var panel: AlertDialog
        panel = NativeUi.dialog(activity) {
            LicenseScreen(BuildConfig.VERSION_NAME, LicenseText.SOURCE_URL, LicenseText.files().toList(), onBack = { panel.dismiss() },
                onSource = {
                    try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LicenseText.SOURCE_URL))) }
                    catch (_: android.content.ActivityNotFoundException) { Toast.makeText(activity, tr("링크를 열 수 있는 앱이 없습니다.", "没有可打开链接的应用。"), Toast.LENGTH_SHORT).show() }
                },
                onFile = { name -> document(activity, name, protect) })
        }
        protect?.accept(panel)
        NativeUi.showFullScreen(panel)
    }

    private fun document(activity: Activity, name: String, protect: Consumer<AlertDialog>?) {
        lateinit var panel: AlertDialog
        panel = NativeUi.dialog(activity) {
            var paragraphs by remember { mutableStateOf<List<String>?>(null) }
            var failed by remember { mutableStateOf(false) }
            LaunchedEffect(name) {
                try {
                    paragraphs = withContext(Dispatchers.IO) {
                        val apk = if (activity.packageName == "io.oniimai.kanade") activity.applicationInfo.sourceDir else GameAssets.apkPath
                        // Bound individual text layouts even for the long NDK notice.
                        LicenseText.read(apk, name).split("\n\n").flatMap { it.chunked(4000) }
                    }
                } catch (_: IOException) { failed = true }
                  catch (_: SecurityException) { failed = true }
            }
            LicenseDocumentScreen(name, paragraphs, failed, onBack = { panel.dismiss() })
        }
        protect?.accept(panel)
        NativeUi.showFullScreen(panel)
    }
}
