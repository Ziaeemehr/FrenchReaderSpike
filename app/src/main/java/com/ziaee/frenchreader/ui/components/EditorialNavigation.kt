package com.ziaee.frenchreader.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.ziaee.frenchreader.R

/**
 * The destinations shared by the editorial bottom navigation on Home,
 * Library, and Resources.
 */
enum class EditorialDestination { HOME, LIBRARY, REVIEW, RESOURCES }

/**
 * Ordinary Material selection semantics (selected/Role.Tab), but with the
 * pill-shaped indicator turned off so the selected destination reads
 * through icon/label color alone rather than an oversized background
 * capsule -- see the redesign spec's bottom-navigation requirement.
 */
@Composable
fun EditorialBottomBar(
    selectedDestination: EditorialDestination,
    onHome: () -> Unit,
    onLibrary: () -> Unit,
    onAddText: () -> Unit,
    onReview: () -> Unit = {},
    onResources: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = selectedDestination == EditorialDestination.HOME,
            onClick = onHome,
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_home)) },
            colors = editorialNavigationItemColors()
        )
        NavigationBarItem(
            selected = selectedDestination == EditorialDestination.LIBRARY,
            onClick = onLibrary,
            icon = { Icon(Icons.Default.LibraryBooks, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_library)) },
            colors = editorialNavigationItemColors()
        )
        NavigationBarItem(
            selected = false,
            onClick = onAddText,
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            label = { Text(stringResource(R.string.action_add_text)) },
            colors = editorialNavigationItemColors()
        )
        NavigationBarItem(
            selected = selectedDestination == EditorialDestination.REVIEW,
            onClick = onReview,
            icon = { Icon(Icons.Default.Layers, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_review)) },
            colors = editorialNavigationItemColors()
        )
        NavigationBarItem(
            selected = selectedDestination == EditorialDestination.RESOURCES,
            onClick = onResources,
            icon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
            label = { Text(stringResource(R.string.nav_resources)) },
            colors = editorialNavigationItemColors()
        )
    }
}

@Composable
private fun editorialNavigationItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.primary,
    selectedTextColor = MaterialTheme.colorScheme.primary,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
    indicatorColor = Color.Transparent
)
