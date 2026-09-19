package com.ziaee.frenchreader.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.ziaee.frenchreader.R
import com.ziaee.frenchreader.data.LibraryFolder
import com.ziaee.frenchreader.data.LibraryTag
import com.ziaee.frenchreader.data.TextDocument

@Composable
internal fun LibraryOrganizerDialog(
    folders: List<LibraryFolder>,
    tags: List<LibraryTag>,
    onDismiss: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (Long, String) -> Unit,
    onDeleteFolder: (Long) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Long) -> Unit,
    onCreateFolderIn: (String, Long?) -> Unit = { name, _ -> onCreateFolder(name) },
    onMoveFolder: (Long, Long?) -> Unit = { _, _ -> }
) {
    var folderName by remember { mutableStateOf("") }
    var tagName by remember { mutableStateOf("") }
    var addSubfolderTo by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var moveFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameTag by remember { mutableStateOf<LibraryTag?>(null) }
    var deleteFolder by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleteTag by remember { mutableStateOf<LibraryTag?>(null) }

    AlertDialog(
        modifier = Modifier.testTag("libraryOrganizerDialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_manage_organizer)) },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                FolderOrganizerSection(
                    folders = folders,
                    input = folderName,
                    onInputChange = { folderName = it },
                    onAdd = { onCreateFolder(folderName); folderName = "" },
                    onAddSubfolder = { addSubfolderTo = it },
                    onRename = { renameFolder = it },
                    onMove = { moveFolder = it },
                    onDelete = { deleteFolder = it }
                )
                OrganizerSection(
                    title = stringResource(R.string.library_tags),
                    items = tags.map { it.id to it.name },
                    input = tagName,
                    inputLabel = stringResource(R.string.library_new_tag),
                    onInputChange = { tagName = it },
                    onAdd = { onCreateTag(tagName); tagName = "" },
                    onRename = { id -> renameTag = tags.first { it.id == id } },
                    onDelete = { id -> deleteTag = tags.first { it.id == id } },
                    testTag = "tag"
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (folderName.isNotBlank()) onCreateFolder(folderName)
                    if (tagName.isNotBlank()) onCreateTag(tagName)
                    onDismiss()
                },
                modifier = Modifier.testTag("libraryOrganizerDone")
            ) { Text(stringResource(R.string.action_close)) }
        }
    )

    addSubfolderTo?.let { parent ->
        NameOrganizerDialog(
            title = R.string.add_subfolder,
            inputLabel = R.string.library_new_folder,
            testTag = "addSubfolderDialog",
            onSave = { onCreateFolderIn(it, parent.id); addSubfolderTo = null },
            onDismiss = { addSubfolderTo = null }
        )
    }
    renameFolder?.let { item ->
        RenameOrganizerDialog(item.name, R.string.library_rename_folder, {
            onRenameFolder(item.id, it); renameFolder = null
        }) { renameFolder = null }
    }
    moveFolder?.let { item ->
        MoveFolderDialog(
            folder = item,
            folders = folders,
            onDismiss = { moveFolder = null },
            onMove = { onMoveFolder(item.id, it); moveFolder = null }
        )
    }
    renameTag?.let { item ->
        RenameOrganizerDialog(item.name, R.string.library_rename_tag, {
            onRenameTag(item.id, it); renameTag = null
        }) { renameTag = null }
    }
    deleteFolder?.let { item ->
        DeleteOrganizerDialog(R.string.library_delete_folder_confirm, {
            onDeleteFolder(item.id); deleteFolder = null
        }) { deleteFolder = null }
    }
    deleteTag?.let { item ->
        DeleteOrganizerDialog(R.string.library_delete_tag_confirm, {
            onDeleteTag(item.id); deleteTag = null
        }) { deleteTag = null }
    }
}

@Composable
private fun FolderOrganizerSection(
    folders: List<LibraryFolder>,
    input: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onAddSubfolder: (LibraryFolder) -> Unit,
    onRename: (LibraryFolder) -> Unit,
    onMove: (LibraryFolder) -> Unit,
    onDelete: (LibraryFolder) -> Unit
) {
    Text(
        stringResource(R.string.library_folders),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp)
    )
    flattenTree(folders).forEach { (folder, depth) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp)
                .testTag("folderRow_${folder.id}")
        ) {
            Text(folder.name, modifier = Modifier.weight(1f))
            IconButton(
                onClick = { onAddSubfolder(folder) },
                modifier = Modifier.testTag("folderAddSubfolder_${folder.id}")
            ) { Icon(Icons.Default.CreateNewFolder, contentDescription = stringResource(R.string.add_subfolder)) }
            IconButton(onClick = { onRename(folder) }, modifier = Modifier.testTag("folderRename_${folder.id}")) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = { onMove(folder) }, modifier = Modifier.testTag("folderMove_${folder.id}")) {
                Icon(Icons.Default.DriveFileMove, contentDescription = stringResource(R.string.move_folder))
            }
            IconButton(onClick = { onDelete(folder) }, modifier = Modifier.testTag("folderDelete_${folder.id}")) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
    OrganizerInput(
        input = input,
        inputLabel = stringResource(R.string.library_new_folder),
        onInputChange = onInputChange,
        onAdd = onAdd,
        testTag = "folder"
    )
}

@Composable
private fun OrganizerSection(
    title: String,
    items: List<Pair<Long, String>>,
    input: String,
    inputLabel: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    onRename: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    testTag: String
) {
    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    items.forEach { (id, name) ->
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(name, modifier = Modifier.weight(1f))
            IconButton(onClick = { onRename(id) }, modifier = Modifier.testTag("${testTag}Rename_$id")) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
            IconButton(onClick = { onDelete(id) }, modifier = Modifier.testTag("${testTag}Delete_$id")) {
                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.action_delete))
            }
        }
    }
    OrganizerInput(input, inputLabel, onInputChange, onAdd, testTag)
}

@Composable
private fun OrganizerInput(
    input: String,
    inputLabel: String,
    onInputChange: (String) -> Unit,
    onAdd: () -> Unit,
    testTag: String,
    inputTestTag: String = "${testTag}NameInput"
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text(inputLabel) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (input.isNotBlank()) onAdd() }),
            modifier = Modifier.weight(1f).testTag(inputTestTag)
        )
        IconButton(onClick = onAdd, enabled = input.isNotBlank(), modifier = Modifier.testTag("${testTag}Add")) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add))
        }
    }
}

@Composable
private fun NameOrganizerDialog(
    title: Int,
    inputLabel: Int,
    testTag: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag(testTag),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(inputLabel)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) }),
                modifier = Modifier.testTag("${testTag}NameInput")
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun RenameOrganizerDialog(
    initialName: String,
    title: Int,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSave(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun DeleteOrganizerDialog(title: Int, onDelete: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        confirmButton = { TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun MoveFolderDialog(
    folder: LibraryFolder,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit
) {
    var selectedId by remember(folder.id, folder.parentId) { mutableStateOf(folder.parentId) }
    val validTargets = remember(folders, folder.id) {
        flattenTree(folders).filter { (candidate, _) -> canMoveFolder(folders, folder.id, candidate.id) }
    }
    AlertDialog(
        modifier = Modifier.testTag("moveFolderDialog_${folder.id}"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.move_folder)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                FolderChoice(
                    id = null,
                    name = stringResource(R.string.top_level),
                    selected = selectedId == null,
                    depth = 0,
                    testTagPrefix = "folderMoveTarget"
                ) { selectedId = null }
                validTargets.forEach { (candidate, depth) ->
                    FolderChoice(
                        id = candidate.id,
                        name = candidate.name,
                        selected = selectedId == candidate.id,
                        depth = depth,
                        testTagPrefix = "folderMoveTarget"
                    ) { selectedId = candidate.id }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(selectedId) }, modifier = Modifier.testTag("moveFolderConfirm")) {
                Text(stringResource(R.string.library_move))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
internal fun MoveToFolderDialog(
    doc: TextDocument? = null,
    folders: List<LibraryFolder>,
    onDismiss: () -> Unit,
    onMove: (Long?) -> Unit,
    onCreateAndMove: (String) -> Unit,
    initialFolderId: Long? = doc?.folderId
) {
    var selectedId by remember(doc?.id, initialFolderId) { mutableStateOf(initialFolderId) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("moveToFolderDialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.library_move_to_folder)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                FolderChoice(
                    id = null,
                    name = stringResource(R.string.library_no_folder),
                    selected = selectedId == null
                ) { selectedId = null }
                flattenTree(folders).forEach { (folder, depth) ->
                    FolderChoice(folder.id, folder.name, selectedId == folder.id, depth) { selectedId = folder.id }
                }
                OrganizerInput(
                    input = newName,
                    inputLabel = stringResource(R.string.library_new_folder),
                    onInputChange = { newName = it },
                    onAdd = { onCreateAndMove(newName) },
                    testTag = "moveNewFolder",
                    inputTestTag = "moveNewFolderInput"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) onCreateAndMove(newName) else onMove(selectedId)
            }) { Text(stringResource(R.string.library_move)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun FolderChoice(
    id: Long?,
    name: String,
    selected: Boolean,
    depth: Int = 0,
    testTagPrefix: String = "folderChoice",
    onSelect: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = (depth * 16).dp).clickable(onClick = onSelect)
            .testTag("${testTagPrefix}_${id ?: "none"}")
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(name)
    }
}

@Composable
internal fun EditTagsDialog(
    doc: TextDocument? = null,
    tags: List<LibraryTag>,
    initialTagIds: Set<Long>,
    onDismiss: () -> Unit,
    onSave: (Set<Long>) -> Unit,
    onCreateAndAssign: (String, Set<Long>) -> Unit,
    addMode: Boolean = false
) {
    var selectedIds by remember(doc?.id, initialTagIds) { mutableStateOf(initialTagIds) }
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        modifier = Modifier.testTag("editTagsDialog"),
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (addMode) R.string.library_add_tags else R.string.library_edit_tags)) },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                tags.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedIds = selectedIds.toggle(tag.id)
                        }.testTag("tagChoice_${tag.id}")
                    ) {
                        Checkbox(
                            checked = tag.id in selectedIds,
                            onCheckedChange = { selectedIds = selectedIds.toggle(tag.id) }
                        )
                        Text(tag.name)
                    }
                }
                OrganizerInput(
                    input = newName,
                    inputLabel = stringResource(R.string.library_new_tag),
                    onInputChange = { newName = it },
                    onAdd = { onCreateAndAssign(newName, selectedIds) },
                    testTag = "editTagsNew",
                    inputTestTag = "editTagsNewInput"
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (newName.isNotBlank()) onCreateAndAssign(newName, selectedIds) else onSave(selectedIds)
            }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

private fun Set<Long>.toggle(id: Long): Set<Long> = toMutableSet().apply {
    if (!add(id)) remove(id)
}
