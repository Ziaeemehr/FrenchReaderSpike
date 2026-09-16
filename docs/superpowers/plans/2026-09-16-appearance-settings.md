# Appearance Settings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an app-wide light/dark theme, three fixed reading-background color presets (independent of the app theme), and four fixed reading font-size presets, all changeable from a new Settings screen and applied live without restarting the app.

**Architecture:** A new `ui/theme/Theme.kt` defines the enums (`ThemeMode`, `ReadingBackground`, `FontScale`), a pure `ReadingPalette` data class + `readingPaletteFor()` mapping (replacing `ReadingScreen.kt`'s current hardcoded `ReadingPalette` object), a `FrenchReaderTheme` composable wrapping `MaterialTheme`, and a lightweight `AppearanceState` object (`mutableStateOf` fields) for live propagation without a `CompositionLocal` or ViewModel. A new `data/AppearancePrefs.kt` persists the three choices via `SharedPreferences`, following the existing `VocabPrefs.kt` pattern exactly. A new `ui/SettingsScreen.kt` lets the user change all three. `MainActivity.kt` initializes `AppearanceState` from prefs and wraps navigation in `FrenchReaderTheme`; `TextsListScreen.kt` gets a settings entry icon; `ReadingScreen.kt`'s hardcoded palette and font-size literals are replaced with values threaded from `AppearanceState`.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 (`compose-bom:2024.06.00`), `SharedPreferences` (no new dependencies).

**Spec:** ROADMAP.md section 7 ("تنظیمات و شخصی‌سازی ظاهر"), specifically its "طراحی فنی" subsection — this plan implements that design exactly; read it for the full rationale (why reading-background is independent of app theme, why no dynamic-color/free color-picker, why a plain state object instead of `CompositionLocal`/DI).

## Global Constraints

- No new Gradle dependencies (`darkColorScheme()`/`lightColorScheme()`/`isSystemInDarkTheme()` are all already available via the existing `androidx.compose.material3:material3` dependency).
- No dynamic color (Material You) — `minSdk 26` is below the API 31 floor it needs; out of scope per ROADMAP.md section 7.
- Reading background is a fixed 3-preset enum (`SEPIA`/`WHITE`/`DARK`), not a free color picker — per ROADMAP.md's explicit decision.
- Font size is a fixed 4-preset enum (`SMALL`/`MEDIUM`/`LARGE`/`XLARGE`), not a continuous slider — per ROADMAP.md's explicit decision.
- `AppearancePrefs.kt` must follow `VocabPrefs.kt`'s exact shape: plain `object`, `context.getSharedPreferences(NAME, Context.MODE_PRIVATE)` obtained fresh per call (no cached reference), `private const val` keys, synchronous get/set taking `Context` as the first parameter, writes via `.edit().put...().apply()`. No Flow/coroutines/DataStore.
- No `CompositionLocal` — settings propagate via the plain `AppearanceState` object's `mutableStateOf` fields, read directly wherever needed (matches this codebase's existing lightweight, DI-free style).
- `ReadingBackground.SEPIA`'s color values must be byte-identical to `ReadingScreen.kt`'s current `ReadingPalette` object, so a user who never touches Settings sees zero visual change.
- `AppearancePrefs` has no unit test (matches the existing precedent: `VocabPrefs.kt`, which is also `Context`-dependent, has no unit test in this project). `readingPaletteFor()` and the dark-theme-resolution logic ARE pure functions and DO get unit tests (Task 1).
- No Compose UI test infrastructure exists in this project (confirmed precedent from every prior UI task) — UI-level verification is manual, on a connected device, per each task's final step.

---

### Task 1: `ui/theme/Theme.kt` — enums, palette mapping, theme wrapper, live state

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt`
- Test: `app/src/test/java/com/ziaee/frenchreader/ui/theme/ThemeTest.kt`

**Interfaces:**
- Produces: `enum class ThemeMode { SYSTEM, LIGHT, DARK }`; `fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean`; `enum class ReadingBackground { SEPIA, WHITE, DARK }`; `data class ReadingPalette(background: Color, ink: Color, inkFaded: Color, highlightBg: Color, highlightInk: Color, divider: Color, accent: Color)`; `fun readingPaletteFor(background: ReadingBackground): ReadingPalette`; `enum class FontScale(val multiplier: Float, val label: String) { SMALL(0.85f, "کوچک"), MEDIUM(1f, "متوسط"), LARGE(1.15f, "بزرگ"), XLARGE(1.3f, "خیلی‌بزرگ") }`; `@Composable fun FrenchReaderTheme(themeMode: ThemeMode, content: @Composable () -> Unit)`; `object AppearanceState { var themeMode by mutableStateOf(ThemeMode.SYSTEM); var readingBackground by mutableStateOf(ReadingBackground.SEPIA); var fontScale by mutableStateOf(FontScale.MEDIUM) }`.
- Consumed by: Task 2 (`AppearancePrefs` stores/restores these enums by name), Task 3 (`SettingsScreen`/`MainActivity` read and write `AppearanceState` and call `FrenchReaderTheme`), Task 4 (`ReadingScreen.kt` reads `AppearanceState.readingBackground`/`.fontScale` and calls `readingPaletteFor`).

- [ ] **Step 1: Write the failing tests**

Create `app/src/test/java/com/ziaee/frenchreader/ui/theme/ThemeTest.kt`:

```kotlin
package com.ziaee.frenchreader.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun `SEPIA palette matches the original hardcoded ReadingPalette values`() {
        val palette = readingPaletteFor(ReadingBackground.SEPIA)
        assertEquals(Color(0xFFFBF6EC), palette.background)
        assertEquals(Color(0xFF2E2A22), palette.ink)
        assertEquals(Color(0xFF6B6252), palette.inkFaded)
        assertEquals(Color(0xFFF6D97A), palette.highlightBg)
        assertEquals(Color(0xFF2E2A22), palette.highlightInk)
        assertEquals(Color(0xFFE6DDC8), palette.divider)
        assertEquals(Color(0xFF8A6D3B), palette.accent)
    }

    @Test
    fun `WHITE and DARK palettes are distinct from SEPIA and from each other`() {
        val sepia = readingPaletteFor(ReadingBackground.SEPIA)
        val white = readingPaletteFor(ReadingBackground.WHITE)
        val dark = readingPaletteFor(ReadingBackground.DARK)
        assertEquals(Color(0xFFFFFFFF), white.background)
        assertEquals(Color(0xFF1A1A1A), dark.background)
        assert(sepia.background != white.background)
        assert(white.background != dark.background)
    }

    @Test
    fun `resolveDarkTheme follows the system when mode is SYSTEM`() {
        assertEquals(true, resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = true))
        assertEquals(false, resolveDarkTheme(ThemeMode.SYSTEM, systemInDarkTheme = false))
    }

    @Test
    fun `resolveDarkTheme ignores the system when mode is an explicit override`() {
        assertEquals(false, resolveDarkTheme(ThemeMode.LIGHT, systemInDarkTheme = true))
        assertEquals(true, resolveDarkTheme(ThemeMode.DARK, systemInDarkTheme = false))
    }

    @Test
    fun `FontScale multipliers are in ascending order from SMALL to XLARGE`() {
        assertEquals(0.85f, FontScale.SMALL.multiplier)
        assertEquals(1f, FontScale.MEDIUM.multiplier)
        assertEquals(1.15f, FontScale.LARGE.multiplier)
        assertEquals(1.3f, FontScale.XLARGE.multiplier)
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.ui.theme.ThemeTest"`
Expected: FAIL — `com.ziaee.frenchreader.ui.theme` package / `Theme.kt` doesn't exist yet (compilation error).

- [ ] **Step 3: Create `Theme.kt`**

Create `app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt`:

```kotlin
package com.ziaee.frenchreader.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Which color scheme the whole app uses -- see ROADMAP.md section 7. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Pure decision logic extracted out of [FrenchReaderTheme] so it's unit
 * testable without needing a Compose/Android runtime for
 * [isSystemInDarkTheme]. */
internal fun resolveDarkTheme(mode: ThemeMode, systemInDarkTheme: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemInDarkTheme
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun FrenchReaderTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    val darkTheme = resolveDarkTheme(themeMode, isSystemInDarkTheme())
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/** Which fixed color set the reading screen uses -- independent of
 * [ThemeMode], like a book-reader app's own reading theme. See
 * ROADMAP.md section 7. */
enum class ReadingBackground { SEPIA, WHITE, DARK }

/** A reading screen's full color set. [ReadingBackground.SEPIA]'s values
 * are byte-identical to the original hardcoded ReadingPalette in
 * ReadingScreen.kt, so someone who never opens Settings sees no change. */
data class ReadingPalette(
    val background: Color,
    val ink: Color,
    val inkFaded: Color,
    val highlightBg: Color,
    val highlightInk: Color,
    val divider: Color,
    val accent: Color
)

fun readingPaletteFor(background: ReadingBackground): ReadingPalette = when (background) {
    ReadingBackground.SEPIA -> ReadingPalette(
        background = Color(0xFFFBF6EC),
        ink = Color(0xFF2E2A22),
        inkFaded = Color(0xFF6B6252),
        highlightBg = Color(0xFFF6D97A),
        highlightInk = Color(0xFF2E2A22),
        divider = Color(0xFFE6DDC8),
        accent = Color(0xFF8A6D3B)
    )
    ReadingBackground.WHITE -> ReadingPalette(
        background = Color(0xFFFFFFFF),
        ink = Color(0xFF1A1A1A),
        inkFaded = Color(0xFF5C5C5C),
        highlightBg = Color(0xFFFFE082),
        highlightInk = Color(0xFF1A1A1A),
        divider = Color(0xFFE0E0E0),
        accent = Color(0xFF3B6EA8)
    )
    ReadingBackground.DARK -> ReadingPalette(
        background = Color(0xFF1A1A1A),
        ink = Color(0xFFE8E4DA),
        inkFaded = Color(0xFFA8A296),
        highlightBg = Color(0xFF4A3F1E),
        highlightInk = Color(0xFFF6D97A),
        divider = Color(0xFF3A3A3A),
        accent = Color(0xFFD8B978)
    )
}

/** How large the reading screen's text renders -- a multiplier applied to
 * every hardcoded sp value in ReadingScreen.kt. See ROADMAP.md section 7. */
enum class FontScale(val multiplier: Float, val label: String) {
    SMALL(0.85f, "کوچک"),
    MEDIUM(1f, "متوسط"),
    LARGE(1.15f, "بزرگ"),
    XLARGE(1.3f, "خیلی‌بزرگ")
}

/** Live, in-memory mirror of the three appearance settings -- read
 * directly by composables (no CompositionLocal, no ViewModel/DI; matches
 * this codebase's existing lightweight style). [com.ziaee.frenchreader.MainActivity]
 * initializes these three fields from [com.ziaee.frenchreader.data.AppearancePrefs]
 * once at startup; SettingsScreen updates both the field here (for
 * immediate recomposition) and the persisted pref (Task 2/3) on every
 * change. */
object AppearanceState {
    var themeMode by mutableStateOf(ThemeMode.SYSTEM)
    var readingBackground by mutableStateOf(ReadingBackground.SEPIA)
    var fontScale by mutableStateOf(FontScale.MEDIUM)
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew testDebugUnitTest --tests "com.ziaee.frenchreader.ui.theme.ThemeTest"`
Expected: PASS (5 tests)

- [ ] **Step 5: Compile the whole project to confirm nothing else broke**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL (this task only adds a new file; nothing existing references it yet)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/theme/Theme.kt app/src/test/java/com/ziaee/frenchreader/ui/theme/ThemeTest.kt
git commit -m "Add theme/palette/font-scale definitions for appearance settings"
```

---

### Task 2: `data/AppearancePrefs.kt` — persistence

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/data/AppearancePrefs.kt`

**Interfaces:**
- Consumes: `ThemeMode`, `ReadingBackground`, `FontScale` (Task 1).
- Produces: `object AppearancePrefs { fun getThemeMode(context: Context): ThemeMode; fun setThemeMode(context: Context, mode: ThemeMode); fun getReadingBackground(context: Context): ReadingBackground; fun setReadingBackground(context: Context, background: ReadingBackground); fun getFontScale(context: Context): FontScale; fun setFontScale(context: Context, scale: FontScale) }` -- consumed by Task 3 (`SettingsScreen`/`MainActivity`).

No test for this task -- it's a thin `Context`-dependent `SharedPreferences` wrapper with no branching logic beyond an `enumValueOf`/fallback, matching `VocabPrefs.kt` (also untested) exactly. Verification is Task 3's manual on-device check (change a setting, kill and reopen the app, confirm it stuck).

- [ ] **Step 1: Create `AppearancePrefs.kt`**

Create `app/src/main/java/com/ziaee/frenchreader/data/AppearancePrefs.kt`:

```kotlin
package com.ziaee.frenchreader.data

import android.content.Context
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingBackground
import com.ziaee.frenchreader.ui.theme.ThemeMode

/**
 * Persists the three appearance choices from the Settings screen -- see
 * ROADMAP.md section 7. Follows the same object + SharedPreferences +
 * Context-param shape as VocabPrefs.kt. Each value is stored by its enum
 * name; an unrecognized or missing name falls back to that setting's
 * default (e.g. after an app update adds/renames an enum constant).
 */
object AppearancePrefs {
    private const val PREFS_NAME = "appearance_prefs"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_READING_BACKGROUND = "reading_background"
    private const val KEY_FONT_SCALE = "font_scale"

    fun getThemeMode(context: Context): ThemeMode = read(context, KEY_THEME_MODE, ThemeMode.SYSTEM)
    fun setThemeMode(context: Context, mode: ThemeMode) = write(context, KEY_THEME_MODE, mode.name)

    fun getReadingBackground(context: Context): ReadingBackground =
        read(context, KEY_READING_BACKGROUND, ReadingBackground.SEPIA)
    fun setReadingBackground(context: Context, background: ReadingBackground) =
        write(context, KEY_READING_BACKGROUND, background.name)

    fun getFontScale(context: Context): FontScale = read(context, KEY_FONT_SCALE, FontScale.MEDIUM)
    fun setFontScale(context: Context, scale: FontScale) = write(context, KEY_FONT_SCALE, scale.name)

    private inline fun <reified T : Enum<T>> read(context: Context, key: String, default: T): T {
        val stored = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getString(key, null)
            ?: return default
        return try {
            enumValueOf<T>(stored)
        } catch (e: IllegalArgumentException) {
            default
        }
    }

    private fun write(context: Context, key: String, value: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(key, value)
            .apply()
    }
}
```

- [ ] **Step 2: Compile to confirm it builds**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/data/AppearancePrefs.kt
git commit -m "Add AppearancePrefs for persisting appearance settings"
```

---

### Task 3: `ui/SettingsScreen.kt` + wire into `MainActivity.kt` and `TextsListScreen.kt`

**Files:**
- Create: `app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`

**Interfaces:**
- Consumes: `ThemeMode`, `ReadingBackground`, `FontScale`, `AppearanceState`, `FrenchReaderTheme` (Task 1); `AppearancePrefs` (Task 2).
- Produces: `@Composable fun SettingsScreen(onBack: () -> Unit)` -- no other task consumes this directly; it's a navigation leaf.

No automated UI test -- no Compose UI test infrastructure in this project (established precedent). Verification is Step 7's manual on-device checklist.

- [ ] **Step 1: Create `SettingsScreen.kt`**

Create `app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt`:

```kotlin
package com.ziaee.frenchreader.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.data.AppearancePrefs
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.FontScale
import com.ziaee.frenchreader.ui.theme.ReadingBackground
import com.ziaee.frenchreader.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تنظیمات") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "بازگشت")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxWidth().padding(padding).padding(20.dp)) {
            Text("تم", style = MaterialTheme.typography.titleMedium)
            SettingsRadioRow(
                label = "پیروی از سیستم",
                selected = AppearanceState.themeMode == ThemeMode.SYSTEM,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.SYSTEM
                    AppearancePrefs.setThemeMode(context, ThemeMode.SYSTEM)
                }
            )
            SettingsRadioRow(
                label = "روشن",
                selected = AppearanceState.themeMode == ThemeMode.LIGHT,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.LIGHT
                    AppearancePrefs.setThemeMode(context, ThemeMode.LIGHT)
                }
            )
            SettingsRadioRow(
                label = "تیره",
                selected = AppearanceState.themeMode == ThemeMode.DARK,
                onClick = {
                    AppearanceState.themeMode = ThemeMode.DARK
                    AppearancePrefs.setThemeMode(context, ThemeMode.DARK)
                }
            )

            Text("رنگ پس‌زمینهٔ خوانش", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            SettingsRadioRow(
                label = "کاغذی (پیش‌فرض)",
                selected = AppearanceState.readingBackground == ReadingBackground.SEPIA,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.SEPIA
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.SEPIA)
                }
            )
            SettingsRadioRow(
                label = "سفید",
                selected = AppearanceState.readingBackground == ReadingBackground.WHITE,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.WHITE
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.WHITE)
                }
            )
            SettingsRadioRow(
                label = "تیره (شبانه)",
                selected = AppearanceState.readingBackground == ReadingBackground.DARK,
                onClick = {
                    AppearanceState.readingBackground = ReadingBackground.DARK
                    AppearancePrefs.setReadingBackground(context, ReadingBackground.DARK)
                }
            )

            Text("اندازهٔ فونت خوانش", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp))
            FontScale.entries.forEach { scale ->
                SettingsRadioRow(
                    label = scale.label,
                    selected = AppearanceState.fontScale == scale,
                    onClick = {
                        AppearanceState.fontScale = scale
                        AppearancePrefs.setFontScale(context, scale)
                    }
                )
            }
        }
    }
}

@Composable
private fun SettingsRadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Column {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected, onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Text(label)
        }
    }
}
```

(`remember`/`mutableStateOf`/`setValue` imports are unused by this file as written -- **do not add them**; this note exists only so the implementer doesn't copy unused imports from Task 1's file by habit. Only the imports listed above are needed.)

- [ ] **Step 2: Wire `FrenchReaderTheme` and the `settings` route into `MainActivity.kt`**

In `app/src/main/java/com/ziaee/frenchreader/MainActivity.kt`, replace this import block:

```kotlin
import androidx.compose.material3.MaterialTheme
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
import com.ziaee.frenchreader.ui.ReadingScreen
import com.ziaee.frenchreader.ui.TextsListScreen
import com.ziaee.frenchreader.ui.VOCAB_SCOPE_ALL
import com.ziaee.frenchreader.ui.VocabListScreen
import com.ziaee.frenchreader.ui.VocabReviewScreen
import com.ziaee.frenchreader.util.IncomingShare
import com.ziaee.frenchreader.util.SharedTextHolder
```

with:

```kotlin
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
```

(`androidx.compose.material3.MaterialTheme` is removed since `MaterialTheme { ... }` is being replaced by `FrenchReaderTheme { ... }` below, which internally calls `MaterialTheme` itself.)

Then replace:

```kotlin
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }

        handleIncomingIntent(intent)

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost()
                }
            }
        }
    }
```

with:

```kotlin
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
```

Then add the `settings` route. Replace:

```kotlin
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
    }
}
```

with:

```kotlin
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
```

And change `AppNavHost`'s one call site of `TextsListScreen` to pass a new callback. Replace:

```kotlin
        composable("texts") {
            TextsListScreen(
                onOpenText = { id -> navController.navigate("reading/$id") },
                onOpenVocab = { navController.navigate("vocab") }
            )
        }
```

with:

```kotlin
        composable("texts") {
            TextsListScreen(
                onOpenText = { id -> navController.navigate("reading/$id") },
                onOpenVocab = { navController.navigate("vocab") },
                onOpenSettings = { navController.navigate("settings") }
            )
        }
```

- [ ] **Step 3: Add a settings entry icon to `TextsListScreen.kt`**

In `app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt`, change the composable's signature. Replace:

```kotlin
fun TextsListScreen(onOpenText: (Long) -> Unit, onOpenVocab: () -> Unit) {
```

with:

```kotlin
fun TextsListScreen(onOpenText: (Long) -> Unit, onOpenVocab: () -> Unit, onOpenSettings: () -> Unit) {
```

Then add the icon button to the `TopAppBar`'s `actions`. Replace:

```kotlin
                actions = {
                    IconButton(onClick = { showVikidiaSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "پیدا کردن مطلب")
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/markdown", "text/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "افزودن از فایل (TXT/MD)")
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = "لغات ذخیره‌شده")
                    }
                }
```

with:

```kotlin
                actions = {
                    IconButton(onClick = { showVikidiaSheet = true }) {
                        Icon(Icons.Default.Search, contentDescription = "پیدا کردن مطلب")
                    }
                    IconButton(onClick = { filePicker.launch(arrayOf("text/plain", "text/markdown", "text/*")) }) {
                        Icon(Icons.Default.FileOpen, contentDescription = "افزودن از فایل (TXT/MD)")
                    }
                    IconButton(onClick = onOpenVocab) {
                        Icon(Icons.Default.MenuBook, contentDescription = "لغات ذخیره‌شده")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "تنظیمات")
                    }
                }
```

(`Icons.Default.Settings` is covered by the file's existing `import androidx.compose.material.icons.filled.*` wildcard -- no new icon import needed.)

- [ ] **Step 4: Compile the whole project**

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run the full unit test suite to confirm nothing broke**

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all existing tests plus Task 1's `ThemeTest` still pass

- [ ] **Step 6: Install and manually verify on device**

Run: `./gradlew assembleDebug installDebug`

1. Tap the new settings icon in the texts list's top bar -- Settings screen opens.
2. Switch theme to "تیره" -- confirm the whole app (texts list, top bars, buttons) switches to a dark Material scheme immediately, without restarting the app.
3. Switch back to "پیروی از سیستم" -- confirm it follows the device's current system light/dark setting.
4. Kill the app fully (swipe away from recents) and reopen it -- confirm the theme choice you left it on is still applied (persistence works).
5. Reading-background and font-size selections won't visibly do anything yet (`ReadingScreen.kt` isn't wired to them until Task 4) -- just confirm the radio selections themselves update correctly and persist across app restart (re-open Settings after restarting the app).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/SettingsScreen.kt app/src/main/java/com/ziaee/frenchreader/MainActivity.kt app/src/main/java/com/ziaee/frenchreader/ui/TextsListScreen.kt
git commit -m "Add Settings screen; wire app-wide theme and settings route"
```

---

### Task 4: Wire `ReadingScreen.kt` to the reading-background palette and font scale

**Files:**
- Modify: `app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt`

**Interfaces:**
- Consumes: `AppearanceState`, `readingPaletteFor`, `ReadingPalette` (from `com.ziaee.frenchreader.ui.theme`, Task 1).
- Produces: nothing consumed by later tasks -- this is the last task in the plan.

No automated UI test (no Compose UI test infra in this project) -- verification is Step 6's manual on-device checklist.

This task replaces every reference to the current module-level `private object ReadingPalette` with a locally-computed `palette: ReadingPalette` value (from `readingPaletteFor(AppearanceState.readingBackground)`), threaded as a parameter into the three private composables that currently read `ReadingPalette` directly (`ChunkParagraph`, `SentenceFlowText`, `PlaybackControls`), and scales every hardcoded `fontSize`/`lineHeight` literal by `AppearanceState.fontScale.multiplier`.

- [ ] **Step 1: Add the import and remove the old hardcoded palette**

Add this import (alongside the other `com.ziaee.frenchreader.*` imports):
```kotlin
import com.ziaee.frenchreader.ui.theme.AppearanceState
import com.ziaee.frenchreader.ui.theme.ReadingPalette
import com.ziaee.frenchreader.ui.theme.readingPaletteFor
```

Delete this entire object (it's replaced by `readingPaletteFor`/`ReadingPalette` from Task 1's `ui/theme/Theme.kt`):
```kotlin
/** A warm, paper-like reading palette -- deliberately not stock Material,
 * because a reading screen should feel like a book, not a form. */
private object ReadingPalette {
    val Background = Color(0xFFFBF6EC)
    val Ink = Color(0xFF2E2A22)
    val InkFaded = Color(0xFF6B6252)
    val HighlightBg = Color(0xFFF6D97A)
    val HighlightInk = Color(0xFF2E2A22)
    val Divider = Color(0xFFE6DDC8)
    val Accent = Color(0xFF8A6D3B)
}
```

- [ ] **Step 2: Compute `palette`/`fontScale` at the top of `ReadingScreen` and rewrite every `ReadingPalette.X` reference inside it to `palette.X`**

At the top of the `ReadingScreen` composable, right after `val state by vm.state.collectAsState()`, add:
```kotlin
    val palette = readingPaletteFor(AppearanceState.readingBackground)
    val fontScale = AppearanceState.fontScale.multiplier
```

Then, throughout the rest of the `ReadingScreen` composable function (its `Scaffold`'s `containerColor`, `topBar`'s `Text`/`Icon` colors, the `LinearProgressIndicator`'s `color`/`trackColor`, and every other direct reference), replace every occurrence of `ReadingPalette.` with `palette.` and lower-case the member name to match the new `ReadingPalette` data class's property names (`Background`->`background`, `Ink`->`ink`, `InkFaded`->`inkFaded`, `HighlightBg`->`highlightBg`, `HighlightInk`->`highlightInk`, `Divider`->`divider`, `Accent`->`accent`). This is a complete, mechanical substitution -- every single `ReadingPalette.X` in the file (including inside `ChunkParagraph`, `SentenceFlowText`, and `PlaybackControls`, handled by Steps 3-5 below) becomes `palette.X` with `X`'s first letter lower-cased, with no exceptions and no remaining references to the old `ReadingPalette` object anywhere. Grep for `ReadingPalette\.` after finishing this task (Step 7) to confirm zero matches remain outside the `ui/theme` package.

Also pass `palette`/`fontScale` down to the three composables that need them. Replace:
```kotlin
            itemsIndexed(state.chunks) { chunkIndex, chunk ->
                ChunkParagraph(
                    chunk = chunk,
                    isCurrentChunk = chunkIndex == state.currentChunkIndex,
                    currentPositionMs = state.currentPositionMs,
                    showTranslation = state.showTranslations,
                    onSentenceClick = { sentence -> vm.seekToSentence(chunkIndex, sentence) },
                    onRetry = { vm.retryChunk(chunkIndex) },
                    onWordLookup = { word, sentenceText ->
                        vm.player.pause()
                        dictionaryTarget = word to sentenceText
                    }
                )
```
with:
```kotlin
            itemsIndexed(state.chunks) { chunkIndex, chunk ->
                ChunkParagraph(
                    chunk = chunk,
                    isCurrentChunk = chunkIndex == state.currentChunkIndex,
                    currentPositionMs = state.currentPositionMs,
                    showTranslation = state.showTranslations,
                    palette = palette,
                    fontScale = fontScale,
                    onSentenceClick = { sentence -> vm.seekToSentence(chunkIndex, sentence) },
                    onRetry = { vm.retryChunk(chunkIndex) },
                    onWordLookup = { word, sentenceText ->
                        vm.player.pause()
                        dictionaryTarget = word to sentenceText
                    }
                )
```

And replace the bottom bar's call site:
```kotlin
        bottomBar = { PlaybackControls(vm, state) }
```
with:
```kotlin
        bottomBar = { PlaybackControls(vm, state, palette) }
```

- [ ] **Step 3: Thread `palette`/`fontScale` through `ChunkParagraph`**

Replace `ChunkParagraph`'s signature:
```kotlin
@Composable
private fun ChunkParagraph(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    showTranslation: Boolean,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit
) {
```
with:
```kotlin
@Composable
private fun ChunkParagraph(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    showTranslation: Boolean,
    palette: ReadingPalette,
    fontScale: Float,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onRetry: () -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit
) {
```

Inside its body, every `ReadingPalette.X` reference becomes `palette.X` (per Step 2's rule -- e.g. `color = ReadingPalette.Ink` becomes `color = palette.ink`, `color = ReadingPalette.Accent` becomes `color = palette.accent`, `color = ReadingPalette.InkFaded` becomes `color = palette.inkFaded`, at every one of its three `SentenceFlowText(...)` calls and its translation-status `Text(...)` calls).

Every hardcoded `fontSize`/`lineHeight` sp literal in this function is multiplied by `fontScale`. Replace:
```kotlin
                    when (chunk.block.type) {
                        BlockType.HEADER -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = headerFontSize(chunk.block.headerLevel),
                            lineHeight = headerLineHeight(chunk.block.headerLevel),
                            fontWeight = FontWeight.Bold,
                            color = ReadingPalette.Ink,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
                        BlockType.LIST_ITEM -> Row {
                            Text(
                                "•  ",
                                fontSize = 19.sp,
                                lineHeight = 29.sp,
                                color = ReadingPalette.Accent
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                SentenceFlowText(
                                    chunk = chunk,
                                    isCurrentChunk = isCurrentChunk,
                                    currentPositionMs = currentPositionMs,
                                    fontSize = 19.sp,
                                    lineHeight = 29.sp,
                                    fontWeight = null,
                                    color = ReadingPalette.Ink,
                                    onSentenceClick = onSentenceClick,
                                    onWordLookup = onWordLookup
                                )
                            }
                        }
                        BlockType.PARAGRAPH -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = 19.sp,
                            lineHeight = 31.sp,
                            fontWeight = null,
                            color = ReadingPalette.Ink,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
                    }
```
with:
```kotlin
                    when (chunk.block.type) {
                        BlockType.HEADER -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = headerFontSize(chunk.block.headerLevel, fontScale),
                            lineHeight = headerLineHeight(chunk.block.headerLevel, fontScale),
                            fontWeight = FontWeight.Bold,
                            color = palette.ink,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
                        BlockType.LIST_ITEM -> Row {
                            Text(
                                "•  ",
                                fontSize = (19f * fontScale).sp,
                                lineHeight = (29f * fontScale).sp,
                                color = palette.accent
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                SentenceFlowText(
                                    chunk = chunk,
                                    isCurrentChunk = isCurrentChunk,
                                    currentPositionMs = currentPositionMs,
                                    fontSize = (19f * fontScale).sp,
                                    lineHeight = (29f * fontScale).sp,
                                    fontWeight = null,
                                    color = palette.ink,
                                    onSentenceClick = onSentenceClick,
                                    onWordLookup = onWordLookup
                                )
                            }
                        }
                        BlockType.PARAGRAPH -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = (19f * fontScale).sp,
                            lineHeight = (31f * fontScale).sp,
                            fontWeight = null,
                            color = palette.ink,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
                    }
```

And replace the translation-status block right after it:
```kotlin
                    if (showTranslation && chunk.block.type != BlockType.HEADER) {
                        Spacer(Modifier.height(6.dp))
                        when (chunk.translationStatus) {
                            ChunkStatus.READY -> chunk.translation?.let {
                                Text(
                                    it,
                                    fontSize = 14.sp,
                                    lineHeight = 21.sp,
                                    fontStyle = FontStyle.Italic,
                                    color = ReadingPalette.InkFaded
                                )
                            }
                            ChunkStatus.LOADING -> Text(
                                "در حال ترجمه...",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = ReadingPalette.InkFaded
                            )
                            ChunkStatus.ERROR -> Text(
                                "ترجمه در دسترس نیست",
                                fontSize = 13.sp,
                                fontStyle = FontStyle.Italic,
                                color = ReadingPalette.InkFaded
                            )
                            ChunkStatus.PENDING -> {}
                        }
                    }
```
with:
```kotlin
                    if (showTranslation && chunk.block.type != BlockType.HEADER) {
                        Spacer(Modifier.height(6.dp))
                        when (chunk.translationStatus) {
                            ChunkStatus.READY -> chunk.translation?.let {
                                Text(
                                    it,
                                    fontSize = (14f * fontScale).sp,
                                    lineHeight = (21f * fontScale).sp,
                                    fontStyle = FontStyle.Italic,
                                    color = palette.inkFaded
                                )
                            }
                            ChunkStatus.LOADING -> Text(
                                "در حال ترجمه...",
                                fontSize = (13f * fontScale).sp,
                                fontStyle = FontStyle.Italic,
                                color = palette.inkFaded
                            )
                            ChunkStatus.ERROR -> Text(
                                "ترجمه در دسترس نیست",
                                fontSize = (13f * fontScale).sp,
                                fontStyle = FontStyle.Italic,
                                color = palette.inkFaded
                            )
                            ChunkStatus.PENDING -> {}
                        }
                    }
```

Further down in the same function, the `ChunkStatus.LOADING`/`ChunkStatus.ERROR`/`ChunkStatus.PENDING` branches also have literals. Replace:
```kotlin
        ChunkStatus.LOADING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = ReadingPalette.Accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "در حال آماده‌سازی صدا برای این بخش...",
                    fontSize = 13.sp,
                    color = ReadingPalette.InkFaded
                )
            }
        }
        ChunkStatus.ERROR -> {
            Column {
                Text(
                    "خطا در تولید صدا برای این بخش: ${chunk.error}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp
                )
                TextButton(onClick = onRetry) { Text("تلاش مجدد") }
            }
        }
        ChunkStatus.PENDING -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    chunk.text,
                    fontSize = 19.sp,
                    lineHeight = 31.sp,
                    color = ReadingPalette.InkFaded
                )
            }
        }
```
with:
```kotlin
        ChunkStatus.LOADING -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = palette.accent
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "در حال آماده‌سازی صدا برای این بخش...",
                    fontSize = (13f * fontScale).sp,
                    color = palette.inkFaded
                )
            }
        }
        ChunkStatus.ERROR -> {
            Column {
                Text(
                    "خطا در تولید صدا برای این بخش: ${chunk.error}",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = (13f * fontScale).sp
                )
                TextButton(onClick = onRetry) { Text("تلاش مجدد") }
            }
        }
        ChunkStatus.PENDING -> {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(
                    chunk.text,
                    fontSize = (19f * fontScale).sp,
                    lineHeight = (31f * fontScale).sp,
                    color = palette.inkFaded
                )
            }
        }
```

- [ ] **Step 4: Scale `headerFontSize`/`headerLineHeight`**

Replace:
```kotlin
private fun headerFontSize(level: Int): TextUnit = when (level) {
    1 -> 26.sp
    2 -> 24.sp
    3 -> 22.sp
    4 -> 20.sp
    5 -> 19.sp
    else -> 18.sp
}

private fun headerLineHeight(level: Int): TextUnit = when (level) {
    1 -> 34.sp
    2 -> 31.sp
    3 -> 29.sp
    4 -> 27.sp
    5 -> 26.sp
    else -> 25.sp
}
```
with:
```kotlin
private fun headerFontSize(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 26f
    2 -> 24f
    3 -> 22f
    4 -> 20f
    5 -> 19f
    else -> 18f
} * fontScale).sp

private fun headerLineHeight(level: Int, fontScale: Float): TextUnit = (when (level) {
    1 -> 34f
    2 -> 31f
    3 -> 29f
    4 -> 27f
    5 -> 26f
    else -> 25f
} * fontScale).sp
```

- [ ] **Step 5: Thread `palette` through `SentenceFlowText`**

Replace `SentenceFlowText`'s signature:
```kotlin
@Composable
private fun SentenceFlowText(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit
) {
```
with:
```kotlin
@Composable
private fun SentenceFlowText(
    chunk: ChunkState,
    isCurrentChunk: Boolean,
    currentPositionMs: Long,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight?,
    color: Color,
    palette: ReadingPalette,
    onSentenceClick: (SentenceBoundary) -> Unit,
    onWordLookup: (word: String, sentence: String) -> Unit
) {
```

Then update the two `ReadingPalette.X` references inside its `buildAnnotatedString` block. Replace:
```kotlin
                if (isActive) {
                    addStyle(
                        SpanStyle(background = ReadingPalette.HighlightBg, color = ReadingPalette.HighlightInk),
                        start, end
                    )
                }
```
with:
```kotlin
                if (isActive) {
                    addStyle(
                        SpanStyle(background = palette.highlightBg, color = palette.highlightInk),
                        start, end
                    )
                }
```

And update all THREE call sites of `SentenceFlowText(...)` inside `ChunkParagraph` (the HEADER, LIST_ITEM, and PARAGRAPH branches shown in Step 3 above) to also pass `palette = palette` as an argument -- e.g. the PARAGRAPH branch becomes:
```kotlin
                        BlockType.PARAGRAPH -> SentenceFlowText(
                            chunk = chunk,
                            isCurrentChunk = isCurrentChunk,
                            currentPositionMs = currentPositionMs,
                            fontSize = (19f * fontScale).sp,
                            lineHeight = (31f * fontScale).sp,
                            fontWeight = null,
                            color = palette.ink,
                            palette = palette,
                            onSentenceClick = onSentenceClick,
                            onWordLookup = onWordLookup
                        )
```
(apply the same `palette = palette,` addition to the HEADER and LIST_ITEM branches' `SentenceFlowText(...)` calls too.)

- [ ] **Step 6: Thread `palette` through `PlaybackControls`**

Replace `PlaybackControls`'s signature:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(vm: ReadingViewModel, state: ReadingUiState) {
```
with:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaybackControls(vm: ReadingViewModel, state: ReadingUiState, palette: ReadingPalette) {
```

Then every `ReadingPalette.X` reference inside its body becomes `palette.X` (per Step 2's rule -- its `Surface(color = ReadingPalette.Background, ...)`, `HorizontalDivider(color = ReadingPalette.Divider)`, and every `tint = ReadingPalette.Ink`/`tint = ReadingPalette.Accent` on its `Icon(...)` calls, and the `Text("${state.speed}x", color = ReadingPalette.Accent, ...)`).

- [ ] **Step 7: Confirm no dangling references, run tests, compile**

Run: `grep -n "ReadingPalette\." app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt`
Expected: no matches at all (every reference was converted to `palette.` in Steps 2-6). If anything shows up, it's a spot this task's earlier steps should have caught -- find and fix it.

Run: `./gradlew testDebugUnitTest`
Expected: BUILD SUCCESSFUL, all tests (including Task 1's `ThemeTest`) pass.

Run: `./gradlew compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Install and manually verify on device**

Run: `./gradlew assembleDebug installDebug`

1. Open a text to read. Confirm it looks exactly as before (sepia/warm background, same font size) -- the default `ReadingBackground.SEPIA`/`FontScale.MEDIUM` must be visually identical to the old hardcoded values.
2. Go to Settings, change reading background to "سفید" -- go back and confirm the open (or a freshly opened) reading screen now shows a white background with dark text, and the active-sentence highlight color also changed.
3. Change reading background to "تیره (شبانه)" -- confirm a dark background with light text and a visible (not washed-out) highlight color.
4. Change font size through all four presets -- confirm paragraph text, list items, and headers all visibly scale together (headers should stay proportionally larger than body text at every setting).
5. Confirm the playback control bar's icon/text colors match whichever reading-background preset is selected (not stuck on the old sepia palette).
6. Confirm the sentence-tap-to-seek and long-press-for-dictionary interactions still work normally (this task doesn't touch that logic, but confirms the `SentenceFlowText` signature change didn't break its `pointerInput` block).

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/com/ziaee/frenchreader/ui/ReadingScreen.kt
git commit -m "Wire ReadingScreen to the reading-background palette and font scale"
```
