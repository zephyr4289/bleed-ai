package com.zai.chat.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import com.zai.chat.ui.theme.SyntaxComment
import com.zai.chat.ui.theme.SyntaxFunction
import com.zai.chat.ui.theme.SyntaxKeyword
import com.zai.chat.ui.theme.SyntaxNumber
import com.zai.chat.ui.theme.SyntaxString
import com.zai.chat.ui.theme.SyntaxType
import com.zai.chat.ui.theme.TextPrimary
import java.util.regex.Pattern

/**
 * High-performance deterministic tokenizer supporting Kotlin, Python, JS/TS, SQL, JSON, Rust, and
 * Shell without runtime webview bridges.
 */
object SyntaxHighlighter {
    private val KEYWORDS = Pattern.compile(
        "\\b(val|var|fun|class|interface|object|return|if|else|when|for|while|import|package|def|async|await|const|let|struct|impl|fn|select|from|where|match|pub|type|switch|case|throw|try|catch|finally|new|this|super|break|continue|yield|enum|sealed|override|private|protected|public|internal|in|is|as|by|true|false|null|nil|None|Self|self)\\b"
    )
    private val STRINGS = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'|`(\\\\.|[^`\\\\])*`")
    private val NUMBERS = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?f?|true|false)\\b")
    private val COMMENTS = Pattern.compile("//.*|/\\*[\\s\\S]*?\\*/|#.*|--.*")
    private val FUNCTIONS = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\()")
    private val TYPES = Pattern.compile("\\b([A-Z][a-zA-Z0-9_]*)\\b")

    fun highlight(code: String, language: String = ""): AnnotatedString {
        return buildAnnotatedString {
            append(code)
            addStyle(SpanStyle(color = TextPrimary), 0, code.length)

            // 1. Strings
            applyPattern(code, STRINGS, SpanStyle(color = SyntaxString))
            // 2. Numbers
            applyPattern(code, NUMBERS, SpanStyle(color = SyntaxNumber))
            // 3. Keywords
            applyPattern(code, KEYWORDS, SpanStyle(color = SyntaxKeyword))
            // 4. Function invocations
            applyPattern(code, FUNCTIONS, SpanStyle(color = SyntaxFunction))
            // 5. Types and Classes
            applyPattern(code, TYPES, SpanStyle(color = SyntaxType))
            // 6. Comments (Overrides prior styling)
            applyPattern(code, COMMENTS, SpanStyle(color = SyntaxComment))
        }
    }

    private fun AnnotatedString.Builder.applyPattern(
        text: String,
        pattern: Pattern,
        style: SpanStyle
    ) {
        val matcher = pattern.matcher(text)
        while (matcher.find()) {
            addStyle(style, matcher.start(), matcher.end())
        }
    }
}
