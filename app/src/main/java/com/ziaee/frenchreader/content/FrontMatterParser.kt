package com.ziaee.frenchreader.content

data class ParsedFrontMatter(val title: String?, val body: String)

fun parseFrontMatter(text: String): ParsedFrontMatter {
    val normalized = text.removePrefix("\uFEFF")
    val lines = normalized.lines()
    if (lines.firstOrNull()?.trim() != "---") return ParsedFrontMatter(null, text)
    val closingIndex = lines.indexOfFirstAfter(1) { it.trim() == "---" }
    if (closingIndex < 0) return ParsedFrontMatter(null, text)

    val rawTitle = lines.subList(1, closingIndex)
        .firstNotNullOfOrNull { line ->
            val match = Regex("^\\s*title\\s*:\\s*(.*?)\\s*$", RegexOption.IGNORE_CASE).matchEntire(line)
            match?.groupValues?.get(1)?.takeIf(String::isNotBlank)
        }
    val title = rawTitle?.let(::unquoteYamlScalar)
    return ParsedFrontMatter(title, lines.drop(closingIndex + 1).joinToString("\n").trimStart('\r', '\n'))
}

private inline fun <T> List<T>.indexOfFirstAfter(start: Int, predicate: (T) -> Boolean): Int {
    for (index in start until size) if (predicate(this[index])) return index
    return -1
}

private fun unquoteYamlScalar(value: String): String = when {
    value.length >= 2 && value.first() == '"' && value.last() == '"' ->
        value.substring(1, value.lastIndex).replace("\\\"", "\"").replace("\\\\", "\\")
    value.length >= 2 && value.first() == '\'' && value.last() == '\'' ->
        value.substring(1, value.lastIndex).replace("''", "'")
    else -> value
}.trim()
