package com.zai.chat.data.repository

/**
 * Converts raw user search text into safe FTS4 MATCH syntax.
 *
 * "what is kotlin coroutines?"  →  "\"what\"* AND \"is\"* AND \"kotlin\"* AND \"coroutines\"*"
 *
 * Strategy: strip every char that isn't a letter/digit/underscore (kills
 * quotes, hyphens, operators like AND/OR/NEAR that would otherwise be parsed
 * as FTS syntax → SQLiteException), quote each token, prefix-match, AND-join.
 * Empty/whitespace input returns null → caller emits empty results.
 *
 * Known limitation (accepted in P3): FTS4's default tokenizer doesn't
 * segment CJK, so CJK prefix search won't behave. Latin scripts are fine.
 */
internal fun sanitizeFtsQuery(raw: String): String? {
    val tokens = raw.trim()
        .split(Regex("\\s+"))
        .map { it.replace(Regex("[^\\p{L}\\p{N}_]"), "") }
        .filter { it.isNotEmpty() }
        .map { "\"$it\"*" }
    return if (tokens.isEmpty()) null else tokens.joinToString(" AND ")
}
