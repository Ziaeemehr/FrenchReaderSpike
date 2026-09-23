package com.ziaee.frenchreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.data.LocalePrefs
import com.ziaee.frenchreader.data.applyAppLanguage
import com.ziaee.frenchreader.ui.ReadingScreen
import com.ziaee.frenchreader.ui.SettingsScreen
import com.ziaee.frenchreader.ui.VOCAB_SCOPE_ALL
import com.ziaee.frenchreader.ui.VocabListScreen
import com.ziaee.frenchreader.ui.VocabReviewScreen
import com.ziaee.frenchreader.ui.home.HomeScreen
import com.ziaee.frenchreader.ui.library.LibraryScreen
import com.ziaee.frenchreader.ui.shared.queryDisplayName
import com.ziaee.frenchreader.resources.ResourcesScreen
import com.ziaee.frenchreader.ui.statistics.StatisticsScreen
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FrenchReaderTheme
import com.ziaee.frenchreader.util.IncomingShare
import com.ziaee.frenchreader.util.MAX_TEXT_IMPORT_BYTES
import com.ziaee.frenchreader.util.SharedTextHolder
import com.ziaee.frenchreader.util.readBytesLimited

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyAppLanguage(LocalePrefs.get(this))

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        handleIncomingIntent(intent)

        // One-time load from persisted prefs into the live state object;
        // SettingsScreen keeps both in sync on every change after this.
        AppearanceState.themeMode = AppearancePrefs.getThemeMode(this)
        AppearanceState.readingBackground = AppearancePrefs.getReadingBackground(this)
        AppearanceState.fontScale = AppearancePrefs.getFontScale(this)
        AppearanceState.highlightColor = AppearancePrefs.getHighlightColor(this)
        AppearanceState.highlightSavedWords = AppearancePrefs.getHighlightSavedWords(this)

        setContent {
            FrenchReaderTheme(themeMode = AppearanceState.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }

    // launchMode="singleTask" (see AndroidManifest.xml) routes a share sent
    // while the app is already running here instead of spawning a second
    // instance.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    /**
     * Accepts a shared text/plain snippet (ACTION_SEND with EXTRA_TEXT), selected text
     * (ACTION_PROCESS_TEXT), a shared file (ACTION_SEND with EXTRA_STREAM), or a TXT/MD
     * file opened directly with this app (ACTION_VIEW) -- per the design doc's "receive
     * text/file from Share" requirement. Reading is UTF-8 only and best-effort: anything
     * that fails to decode is silently ignored rather than crashing the share flow.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val streamUri = getStreamExtra(intent)
                if (streamUri != null && isEpub(intent, streamUri)) {
                    SharedTextHolder.post(IncomingShare("", "", streamUri))
                    return
                }
                val body = sharedText ?: streamUri?.let(::readTextFromUri)
                if (!body.isNullOrBlank()) {
                    val title = streamUri?.let { queryDisplayName(this, it) } ?: getString(R.string.text_untitled)
                    SharedTextHolder.post(IncomingShare(title, body))
                }
            }
            Intent.ACTION_PROCESS_TEXT -> {
                val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
                if (!text.isNullOrBlank()) {
                    SharedTextHolder.post(IncomingShare(getString(R.string.text_untitled), text))
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    if (isEpub(intent, uri)) {
                        SharedTextHolder.post(IncomingShare("", "", uri))
                        return
                    }
                    val body = readTextFromUri(uri)
                    if (!body.isNullOrBlank()) {
                        val title = queryDisplayName(this, uri) ?: getString(R.string.text_untitled)
                        SharedTextHolder.post(IncomingShare(title, body))
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun getStreamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM)

    private fun readTextFromUri(uri: Uri): String? = try {
        contentResolver.openInputStream(uri)?.use {
            it.readBytesLimited(MAX_TEXT_IMPORT_BYTES).toString(Charsets.UTF_8)
        }
    } catch (e: Exception) {
        null
    }

    private fun isEpub(intent: Intent, uri: Uri): Boolean =
        intent.type == "application/epub+zip" ||
            contentResolver.getType(uri) == "application/epub+zip"
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    // Home <-> Library is bottom-navigation, not a push/pop stack: reusing
    // the start destination's saved state avoids piling up duplicate Home
    // or Library entries on every tab switch (see the implementation
    // plan's Task 8, "Avoid duplicate Home/Library destinations").
    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onOpenText = { id -> navController.navigate("reading/$id") },
                onOpenLibrary = { navigateToTab("library") },
                onOpenVocab = { navController.navigate("vocab") },
                onOpenStatistics = { navController.navigate("statistics") },
                onOpenSettings = { navController.navigate("settings") },
                onOpenResources = { navigateToTab("resources") },
                onStartReview = { navController.navigate("vocab_review/$VOCAB_SCOPE_ALL") }
            )
        }
        composable("library") {
            LibraryScreen(
                onOpenText = { id -> navController.navigate("reading/$id") },
                onOpenHome = { navigateToTab("home") },
                onOpenResources = { navigateToTab("resources") },
                onReview = { navController.navigate("vocab_review/$VOCAB_SCOPE_ALL") }
            )
        }
        composable("resources") {
            ResourcesScreen(
                onOpenHome = { navigateToTab("home") },
                onOpenLibrary = { navigateToTab("library") },
                onAddText = { navigateToTab("home") },
                onReview = { navController.navigate("vocab_review/$VOCAB_SCOPE_ALL") }
            )
        }
        composable(
            "reading/{textId}",
            arguments = listOf(navArgument("textId") { type = NavType.LongType })
        ) { backStackEntry ->
            val textId = backStackEntry.arguments?.getLong("textId") ?: return@composable
            ReadingScreen(
                textId = textId,
                onBack = { navController.popBackStack() },
                onOpenVocab = { navController.navigate("vocab") }
            )
        }
        composable("vocab") {
            VocabListScreen(
                onBack = { navController.popBackStack() },
                onOpenReview = { scope -> navController.navigate("vocab_review/$scope") }
            )
        }
        composable(
            "vocab_review/{scope}",
            arguments = listOf(navArgument("scope") { type = NavType.LongType })
        ) { backStackEntry ->
            val scope = backStackEntry.arguments?.getLong("scope") ?: VOCAB_SCOPE_ALL
            VocabReviewScreen(scope = scope, onBack = { navController.popBackStack() }, onOpenSettings = { navController.navigate("settings") })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable("statistics") {
            StatisticsScreen(onBack = { navController.popBackStack() })
        }
    }
}
