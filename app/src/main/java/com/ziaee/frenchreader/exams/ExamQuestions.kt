package com.ziaee.frenchreader.exams

import android.content.res.AssetManager
import org.json.JSONObject

data class ExamQuestionText(val fr: String, val fa: String)
data class ExamQuestionItem(val id: String, val count: Int, val months: List<String>, val texts: List<ExamQuestionText>) {
    val lastSeen: String get() = months.last()
}
data class ExamQuestionTask(val id: String, val skill: String, val task: Int, val titleFr: String, val exportedOn: String, val items: List<ExamQuestionItem>)
data class ExamQuestionSet(val variant: String, val source: ExamSource, val tasks: List<ExamQuestionTask>)

fun parseExamQuestions(json: String): ExamQuestionSet {
    val root = JSONObject(json)
    require(root.getInt("version") == 1)
    val source = root.getJSONObject("source")
    val tasks = root.getJSONArray("tasks")
    return ExamQuestionSet(root.getString("variant"), ExamSource("questions", source.getString("title"), source.getString("url")),
        (0 until tasks.length()).mapNotNull { index ->
            runCatching {
                val task = tasks.getJSONObject(index)
                val entries = task.getJSONArray("items")
                val items = (0 until entries.length()).mapNotNull { itemIndex ->
                    runCatching {
                        val item = entries.getJSONObject(itemIndex)
                        val months = item.getJSONArray("months").let { array -> (0 until array.length()).map { array.getString(it) } }
                        val texts = item.getJSONArray("texts").let { array -> (0 until array.length()).map {
                            val text = array.getJSONObject(it)
                            ExamQuestionText(text.getString("fr"), text.optString("fa"))
                        } }
                        require(months.isNotEmpty() && months.all { it.matches(Regex("\\d{4}-\\d{2}")) })
                        require(texts.isNotEmpty() && texts.all { it.fr.isNotBlank() })
                        ExamQuestionItem(item.getString("id"), item.getInt("count"), months, texts)
                    }.getOrNull()
                }
                require(items.isNotEmpty())
                ExamQuestionTask(task.getString("id"), task.getString("skill"), task.getInt("task"), task.getString("titleFr"), task.getString("exportedOn"), items)
            }.getOrNull()
        })
}

fun loadExamQuestions(assets: AssetManager, file: String): ExamQuestionSet =
    assets.open("exams/$file").bufferedReader().use { parseExamQuestions(it.readText()) }

/** Number of subjects in [file], from the small "-meta" file the build script writes next to it. */
fun loadExamQuestionTotal(assets: AssetManager, file: String): Int =
    assets.open("exams/${file.removeSuffix(".json")}-meta.json").bufferedReader().use { JSONObject(it.readText()).getInt("total") }
