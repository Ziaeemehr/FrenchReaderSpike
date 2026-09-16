package com.ziaee.frenchreader

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.ui.ReadingScreen
import com.ziaee.frenchreader.ui.SettingsScreen
import com.ziaee.frenchreader.ui.TextsListScreen
import com.ziaee.frenchreader.ui.VOCAB_SCOPE_ALL
import com.ziaee.frenchreader.ui.VocabListScreen
import com.ziaee.frenchreader.ui.VocabReviewScreen
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FrenchReaderTheme
import com.ziaee.frenchreader.util.IncomingShare
import com.ziaee.frenchreader.util.SharedTextHolder

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        handleIncomingIntent(intent)

        // One-time load from persisted prefs into the live state object;
        // SettingsScreen keeps both in sync on every change after this.
        AppearanceState.themeMode = AppearancePrefs.getThemeMode(this)
        AppearanceState.readingBackground = AppearancePrefs.getReadingBackground(this)
        AppearanceState.fontScale = AppearancePrefs.getFontScale(this)

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
     * Accepts a shared text/plain snippet (ACTION_SEND with EXTRA_TEXT), a
     * shared file (ACTION_SEND with EXTRA_STREAM), or a TXT/MD file opened
     * directly with this app (ACTION_VIEW) -- per the design doc's "receive
     * text/file from Share" requirement. Reading is UTF-8 only and best-
     * effort: anything that fails to decode is silently ignored rather than
     * crashing the share flow.
     */
    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                val streamUri = getStreamExtra(intent)
                val body = sharedText ?: streamUri?.let { readTextFromUri(it) }
                if (!body.isNullOrBlank()) {
                    val title = streamUri?.let { queryDisplayName(it) } ?: "متن اشتراک‌گذاری‌شده"
                    SharedTextHolder.post(IncomingShare(title, body))
                }
            }
            Intent.ACTION_VIEW -> {
                intent.data?.let { uri ->
                    val body = readTextFromUri(uri)
                    if (!body.isNullOrBlank()) {
                        val title = queryDisplayName(uri) ?: "فایل وارد شده"
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
        contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
    } catch (e: Exception) {
        null
    }

    private fun queryDisplayName(uri: Uri): String? = try {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) c.getString(idx)?.substringBeforeLast(".") else null
            } else null
        }
    } catch (e: Exception) {
        null
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "texts") {
        composable("texts") {
            TextsListScreen(
                onOpenText = { id -> navController.navigate("reading/$id") },
                onOpenVocab = { navController.navigate("vocab") },
                onOpenSettings = { navController.navigate("settings") }
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
            VocabReviewScreen(scope = scope, onBack = { navController.popBackStack() })
        }
        composable("settings") {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
