package io.oniimai.kanade

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.AssetManager
import android.content.res.Resources
import android.content.res.Configuration
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.createLifecycleAwareWindowRecomposer
import androidx.lifecycle.*
import androidx.savedstate.*

/** Owns the Jetpack view tree when Unity's Activity supplies none. Never modifies the game tree. */
internal class ComposeHost(activity: Activity, content: @Composable () -> Unit) : FrameLayout(activity), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore = ViewModelStore()
    private var everAttached = false
    private val compose: ComposeView
    init {
        saved.performAttach(); saved.performRestore(null)
        registry.currentState = Lifecycle.State.CREATED
        setViewTreeLifecycleOwner(this)
        setViewTreeSavedStateRegistryOwner(this)
        setViewTreeViewModelStoreOwner(this)
        compose = ComposeView(moduleContext(activity)).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent { OniTheme(content) }
        }
        addView(compose, LayoutParams(-1, -1))
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // WindowRecomposer otherwise searches Unity's root, outside our isolated owner tree.
        compose.setParentCompositionContext(createLifecycleAwareWindowRecomposer(lifecycle = registry))
        everAttached = true; updateLifecycle()
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); updateLifecycle() }
    override fun onVisibilityAggregated(visible: Boolean) { super.onVisibilityAggregated(visible); updateLifecycle() }
    override fun onConfigurationChanged(newConfig: Configuration) {
        @Suppress("DEPRECATION")
        compose.context.resources.updateConfiguration(newConfig, context.resources.displayMetrics)
        super.onConfigurationChanged(newConfig)
    }
    private fun updateLifecycle() {
        if (!everAttached) return
        registry.currentState = if (isAttachedToWindow && isShown) {
            if (hasWindowFocus()) Lifecycle.State.RESUMED else Lifecycle.State.STARTED
        } else Lifecycle.State.CREATED
    }
    override fun onDetachedFromWindow() {
        registry.currentState = Lifecycle.State.CREATED
        compose.disposeComposition(); viewModelStore.clear()
        super.onDetachedFromWindow()
    }
    companion object {
        private var resourceCache = java.lang.ref.WeakReference<Resources>(null)
        private var resourcePath: String? = null
        private fun moduleContext(activity: Activity): Context {
            if (activity.packageName == "io.oniimai.kanade") return activity
            val resources = try {
                val path = GameAssets.apkPath ?: error("Module APK path missing")
                val cached = resourceCache.get()
                if (resourcePath == path && cached != null && cached.configuration == activity.resources.configuration) cached
                else {
                    val assets = AssetManager::class.java.getDeclaredConstructor().newInstance()
                    AssetManager::class.java.getMethod("addAssetPath", String::class.java).invoke(assets, path)
                    @Suppress("DEPRECATION")
                    Resources(assets, activity.resources.displayMetrics, activity.resources.configuration).also {
                        resourceCache = java.lang.ref.WeakReference(it); resourcePath = path
                    }
                }
            } catch (e: Exception) {
                activity.createPackageContext("io.oniimai.kanade", 0).resources
            }
            return object : ContextWrapper(activity) {
                override fun getResources() = resources
                override fun getAssets() = resources.assets
                override fun getClassLoader() = ComposeHost::class.java.classLoader!!
                override fun getApplicationContext(): Context = this
                override fun getTheme(): Resources.Theme = resources.newTheme().apply { applyStyle(android.R.style.Theme_Material_Light_NoActionBar, true) }
            }
        }
    }
}
