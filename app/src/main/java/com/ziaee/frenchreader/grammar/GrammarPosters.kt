package com.ziaee.frenchreader.grammar

import android.content.res.AssetManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.ui.theme.FrenchReaderDesign
import com.ziaee.frenchreader.ui.theme.LocalGrammarColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A summary poster (PDF) for one or more notes. Posters are listed in
 * assets/grammar/posters/posters.json and their files sit next to it; the list is empty until
 * the first posters are produced, and the PDF viewer is not built yet.
 */
data class GrammarPoster(val id: String, val titleFr: String, val titleFa: String, val file: String, val notes: List<String>)

fun parseGrammarPosters(json: String): List<GrammarPoster> = runCatching {
    val root = JSONObject(json)
    require(root.getInt("version") == 1)
    val posters = root.getJSONArray("posters")
    (0 until posters.length()).mapNotNull { index ->
        runCatching {
            val poster = posters.getJSONObject(index)
            val notes = poster.optJSONArray("notes")
            GrammarPoster(
                id = poster.getString("id"),
                titleFr = poster.getString("titleFr"),
                titleFa = poster.getString("titleFa"),
                file = poster.getString("file"),
                notes = (0 until (notes?.length() ?: 0)).map { notes!!.getString(it) }
            )
        }.getOrNull()
    }
}.getOrDefault(emptyList())

fun loadGrammarPosters(assets: AssetManager): List<GrammarPoster> =
    runCatching { assets.open("grammar/posters/posters.json").bufferedReader().use { parseGrammarPosters(it.readText()) } }
        .getOrDefault(emptyList())

@Composable
fun GrammarPostersScreen(onBack: () -> Unit) {
    val assets = LocalContext.current.assets
    val posters by produceState<List<GrammarPoster>?>(null, assets) {
        value = withContext(Dispatchers.IO) { loadGrammarPosters(assets) }
    }
    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(FrenchReaderDesign.spacing.small),
            verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.xSmall)
        ) {
            item(key = "back") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.accessibility_back))
                    }
                    Text(stringResource(R.string.grammar_posters), style = MaterialTheme.typography.titleMedium)
                }
            }
            if (posters?.isEmpty() == true) item(key = "empty") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = FrenchReaderDesign.spacing.xLarge),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(FrenchReaderDesign.spacing.small)
                ) {
                    Icon(
                        Icons.Default.Collections, contentDescription = null,
                        tint = LocalGrammarColors.current.accent,
                        modifier = Modifier.size(FrenchReaderDesign.sizes.touchTarget)
                    )
                    Text(
                        stringResource(R.string.grammar_posters_empty),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            // Titles only for now: opening a poster waits for the PDF viewer.
            items(posters.orEmpty(), key = { it.id }) { poster ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(FrenchReaderDesign.spacing.small)) {
                        Text(poster.titleFr, style = MaterialTheme.typography.bodyLarge)
                        Text(poster.titleFa, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
