package io.oniimai.kanade

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.extra.SuperArrow
import java.io.IOException
import java.util.function.Consumer

/** Informational dialogs share the existing theme, back behavior and input guard. */
internal object LicenseUi {
    private fun tr(ko: String, zh: String) = GameUi.tr(ko, zh)

    @JvmStatic fun show(activity: Activity, protect: Consumer<AlertDialog>?) {
        lateinit var panel: AlertDialog
        panel = NativeUi.dialog(activity) {
            NativeUi.Page(tr("라이선스 · 출처", "许可与来源"), back = { panel.dismiss() }) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
                    item { Card(insideMargin = PaddingValues(OniTokens.inset)) {
                        Text("Oniimai ${BuildConfig.VERSION_NAME} · GPL-3.0-only")
                        Spacer(Modifier.height(OniTokens.gap))
                        Text(tr("비공식 LSPosed 모듈 · kyarameru0\n코드·테스트·문서는 Codex를 사용해 작성·수정했습니다. 외부 코드의 저작권과 라이선스는 각 권리자에게 있습니다.", "非官方 LSPosed 模块 · kyarameru0\n代码、测试和文档使用 Codex 编写和修改。第三方代码的版权和许可归相应权利人所有。"))
                        Spacer(Modifier.height(OniTokens.gap))
                        Text(tr("GPL 조건에 따라 사용·수정·재배포할 수 있으며 보증은 제공되지 않습니다. FeliCa 디코더와 외부 구성요소의 조건도 적용됩니다. 게임·서비스·상표의 권한은 이 라이선스에 포함되지 않습니다.", "可依 GPL 条款使用、修改和再分发，不提供担保。FeliCa 解码器及第三方组件的许可条件同样适用。本许可不授予游戏、服务或商标的权利。"))
                    } }
                    item { Card {
                        SuperArrow(title = tr("소스 코드와 빌드 방법", "源代码与构建说明"), summary = LicenseText.SOURCE_URL, onClick = {
                            try { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LicenseText.SOURCE_URL))) }
                            catch (_: android.content.ActivityNotFoundException) { Toast.makeText(activity, tr("링크를 열 수 있는 앱이 없습니다.", "没有可打开链接的应用。"), Toast.LENGTH_SHORT).show() }
                        })
                    } }
                    item { Card(insideMargin = PaddingValues(OniTokens.inset)) {
                        Text(tr("APK와 함께 받은 같은 버전의 Source ZIP에도 소스와 빌드 방법이 있습니다. 저장소가 비공개라면 배포자에게 해당 소스를 요청하세요. 아래 원문은 인터넷 없이 볼 수 있습니다.", "随 APK 提供的同版本 Source ZIP 也包含源代码和构建说明。若仓库为私有，请向分发者索取对应源代码。以下许可原文可离线查看。"))
                    } }
                    item { Card {
                        LicenseText.files().forEach { name ->
                            SuperArrow(title = name, onClick = { document(activity, name, protect) })
                        }
                    } }
                }
            }
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
            NativeUi.Page(name, back = { panel.dismiss() }) {
                val content = paragraphs
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(OniTokens.inset), verticalArrangement = Arrangement.spacedBy(OniTokens.gap)) {
                    if (failed) item { Text(tr("동봉된 원문을 불러오지 못했습니다. 같은 버전의 Source ZIP에서 licenses 폴더를 확인하세요.", "无法载入随附原文。请查看同版本 Source ZIP 中的 licenses 文件夹。")) }
                    else if (content == null) item { Text(tr("불러오는 중…", "正在载入…")) }
                    else items(content) { paragraph -> SelectionContainer { Text(paragraph, modifier = Modifier.fillMaxWidth()) } }
                }
            }
        }
        protect?.accept(panel)
        NativeUi.showFullScreen(panel)
    }
}
