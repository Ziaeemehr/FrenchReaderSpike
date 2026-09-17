package com.ziaee.frenchreader.llm

data class AssistantPrompt(
    val systemPrompt: String,
    val userPrompt: String,
    val grammar: String?,
    val maxTokens: Int,
)

object Prompts {
    private const val SYSTEM_BASE =
        "Tu es un assistant pour un apprenant de français. Réponds uniquement en français, " +
            "de façon concise et directe, sans répéter la consigne."

    fun summarize(sentence: String): AssistantPrompt = AssistantPrompt(
        systemPrompt = SYSTEM_BASE,
        userPrompt = "Résume ce texte en 2 phrases maximum, en français.\n\nTexte:\n$sentence",
        grammar = null,
        maxTokens = 120,
    )

    fun explainGrammar(sentence: String): AssistantPrompt = AssistantPrompt(
        systemPrompt = SYSTEM_BASE,
        userPrompt = "Explique en français simple le temps verbal utilisé dans cette phrase, " +
            "et pourquoi ce temps est utilisé ici. Ne choisis pas une autre phrase, explique " +
            "uniquement celle-ci.\n\nPhrase:\n$sentence",
        grammar = null,
        maxTokens = 150,
    )

    fun simplifyToA2(sentence: String): AssistantPrompt = AssistantPrompt(
        systemPrompt = SYSTEM_BASE,
        userPrompt = "Réécris ce texte pour un niveau A2 : phrases très courtes (moins de 12 " +
            "mots chacune) et vocabulaire simple.\n\n" +
            "Exemple : \"Bien que la situation économique se soit nettement améliorée au cours " +
            "des derniers mois, de nombreux ménages continuent de rencontrer des difficultés.\" " +
            "devient \"L'économie va mieux. Mais beaucoup de familles ont encore des problèmes.\"" +
            "\n\nTexte à réécrire:\n$sentence",
        grammar = null,
        maxTokens = 200,
    )

    fun extractVocabulary(sentence: String): AssistantPrompt = AssistantPrompt(
        systemPrompt = SYSTEM_BASE,
        userPrompt = "Extrais les mots ou expressions difficiles de cette phrase pour un " +
            "apprenant de niveau B1 (entre 1 et 5), sous forme de JSON avec les clés \"mot\" " +
            "et \"definition_simple\" (définition en français simple). Réponds uniquement " +
            "avec le JSON.\n\nPhrase:\n$sentence",
        grammar = VocabGrammar.GBNF,
        maxTokens = 250,
    )
}
