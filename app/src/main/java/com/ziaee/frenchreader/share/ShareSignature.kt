package com.ziaee.frenchreader.share

const val SHARE_GITHUB_URL = "https://github.com/Ziaeemehr/FrenchReaderSpike"
const val SHARE_BAZAAR_URL = "https://cafebazaar.ir/app/com.ziaee.frenchreader"

/** The signature every shared or exported file ends with: the app name and where to get it. */
fun shareSignatureLines(appName: String): List<String> =
    listOf(appName, "GitHub: $SHARE_GITHUB_URL", "Bazaar: $SHARE_BAZAAR_URL")

/** Blank lines between the lines keep them apart in Markdown and still read well as plain text. */
fun shareSignatureMarkdown(appName: String): String = shareSignatureLines(appName).joinToString("\n\n")
