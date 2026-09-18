package com.ziaee.frenchreader.backup

import org.json.JSONObject

internal fun parseBackupFileId(responseJson: String, fileName: String): String? {
    val files = JSONObject(responseJson).optJSONArray("files") ?: return null
    for (i in 0 until files.length()) {
        val file = files.getJSONObject(i)
        if (file.optString("name") == fileName) return file.optString("id")
    }
    return null
}
