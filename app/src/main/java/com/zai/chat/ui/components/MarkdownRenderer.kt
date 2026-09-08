package com.zai.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.ShapeCodeBlock

/*
 * ── BLOCK MODEL ────────────────────────────────────────────────────────
 * Line-based block parser: fences, headings, quotes, lists, rules, tables
 * (monospace fallback), paragraphs. Deliberately no nested-list recursion
 * beyond indent levels and no GFM table grids — chat output rarely needs
 * them; degrade visibly rather than crash or mis-render.
 */
private sealed class Block {
    data class Paragraph(val text: String) : Block()
    data class Heading(val level: Int, val text: String) : Block()
    data class Code(val language: String, val code: String, val closed: Boolean) : Block()
    data class Quote(val lines: List<String>) : Block()
    data class ListItem(val indent: Int, val marker: String, val text: String) : Block()
    data object Rule : Block()
    data class TableFallback(val lines: List<String>) : Block()
}

private val listItemRegex = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)$""")
private val hrRegex = Regex("""^\s*([-*_])\s*(\1\s*){2,}$""")
private val fenceRegex = Regex("""^\s*```\s*(\S*)""")

private fun parseBlocks(markdown: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val lines = markdown.replace("\r\n", "\n").split("\n")
    var i = 0
    val para = mutableListOf<String>()

    fun flushParagraph() {
        if (para.isNotEmpty()) {
            blocks += Block.Paragraph(para.joinToString("\n"))
            para.clear()
        }
    }

    while (i < lines.size) {
        val line = lines[i]
        val fence = fenceRegex.find(line)
        when {
            fence != null -> {
                flushParagraph()
                val language = fence.groupValues[1]
                val code = StringBuilder()
                i++
                var closed = false
                while (i < lines.size) {
                    if (fenceRegex.matches(lines[i])) { closed = true; i++; break }
                    code.appendLine(lines[i]); i++
                }
                // Streaming: unterminated fence still renders (live code)
                blocks += Block.Code(
                    language,
                    code.toString().trimEnd('\n'),
                    closed
                )
            }
            line.startsWith("#") -> {
                flushParagraph()
                val level = line.takeWhile { it == '#' }.length.coerceIn(1, 6)
                blocks += Block.Heading(level, line.dropWhile { it == '#' }.trim())
                i++
            }
            hrRegex.matches(line) -> { flushParagraph(); blocks += Block.Rule; i++ }
            line.trimStart().startsWith(">") -> {
                flushParagraph()
                val quote = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quote += lines[i].trimStart().removePrefix(">").removePrefix(" ").trimStart()
                    i++
                }
                blocks += Block.Quote(quote)
            }
            line.trimStart().startsWith("|") && line.indexOf("|", startIndex = 2) != -1 -> {
                flushParagraph()
                val table = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                    table += lines[i].trimEnd(); i++
                }
                blocks += Block.TableFallback(table)
            }
            listItemRegex.matches(line) -> {
                flushParagraph()
                while (i < lines.size) {
                    val m = listItemRegex.find(lines[i]) ?: break
                    val indent = (m.groupValues[1].length / 2).coerceAtMost(3)
                    blocks += Block.ListItem(indent, m.groupValues[2], m.groupValues[3])
                    i++
                }
            }
            line.isBlank() -> { flushParagraph(); i++ }
            else -> { para += line; i++ }
        }
    }
    flushParagraph()
    return blocks
}

/*
 * ── INLINE MODEL ───────────────────────────────────────────────────────
 * Single alternation regex: code > link > bold > italic (priority by order).
 * Known limits (accepted): no nested ***bold-italic*** merging, no \* escapes.
 */
private val inlineRegex = Regex(
    "`([^`\\n]+?)`" +                                  // g1 code
    "|\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)" +       // g2 label, g3 url
    "|\\*\\*(.+?)\\*\\*" +                             // g4 bold
    "|__(.+?)__" +                                     // g5 bold
    "|(?<![*\\w])\\*([^*\\n]+?)\\*(?![*\\w])" +        // g6 italic
    "|(?<![_\\w])_([^_\\n]+?)_(?![\\w_])"              // g7 italic
)

private val bareUrlRegex = Regex("""(https?://[^\s<>()\[\]{}]+[^\s<>()\[\]{}.,;:!?])""")

private data class Styles(
    val base: TextStyle,
    val codeBg: Color,
    val linkColor: Color,
    val mono: FontFamily
)

private fun AnnotatedString.Builder.appendInline(
    text: String,
    s: Styles
) {
    var last = 0
    for (m in inlineRegex.findAll(text)) {
        if (m.range.first > last) append(text.substring(last, m.range.first))
        val g = m.groupValues
            g[1].isNotEmpty() -> pushStyle(
                SpanStyle(
                    fontFamily = s.mono,
                    background = s.codeBg,
                    color = s.linkColor,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            ).also { append(" ${g[1]} "); pop() }
            g[2].isNotEmpty() -> {
                pushStyle(SpanStyle(color = s.linkColor, textDecoration = TextDecoration.Underline))
                append(g[2])
                pop()
            }
            g[4].isNotEmpty() -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                .also { appendInline(g[4], s); pop() }   // recurse: bold+inline-code
            g[5].isNotEmpty() -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                .also { appendInline(g[5], s); pop() }
            g[6].isNotEmpty() -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                .also { appendInline(g[6], s); pop() }
            g[7].isNotEmpty() -> pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                .also { appendInline(g[7], s); pop() }
        }
        last = m.range.last + 1
    }
    if (last < text.length) {
        val tail = text.substring(last)
        var tLast = 0
        for (u in bareUrlRegex.findAll(tail)) {
            if (u.range.first > tLast) append(tail.substring(tLast, u.range.first))
            pushStyle(SpanStyle(color = s.linkColor, textDecoration = TextDecoration.Underline))
            append(u.value)
            pop()
            tLast = u.range.last + 1
        }
        if (tLast < tail.length) append(tail.substring(tLast))
    }
}

private const val CURSOR = "▍"

/**
 * The renderer. [isStreaming] appends the cursor glyph to the final block
 * (inline for paragraphs, on its own line after code/lists — correct under
 * wrapping, unlike the PDF's floated composable).
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    isStreaming: Boolean = false,
    onCodeInsert: ((String) -> Unit)? = null,
    style: TextStyle = MaterialTheme.typography.bodyLarge
) {
    if (markdown.isEmpty()) return
    val blocks = remember(markdown) { parseBlocks(markdown) }
    val styles = Styles(
        base = style,
        codeBg = MaterialTheme.colorScheme.surfaceVariant,
        linkColor = MaterialTheme.colorScheme.primary,
        mono = FontFamily.Monospace
    )

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            val isLast = index == blocks.lastIndex
            when (block) {
                is Block.Paragraph -> {
                    val annotated = buildAnnotatedString {
                        appendInline(block.text, styles)
                        if (isStreaming && isLast) {
                            pushStyle(SpanStyle(color = styles.linkColor))
                            append(CURSOR); pop()
                        }
                    }
                    Text(annotated, style = style)
                }
                is Block.Heading -> {
                    val hStyle = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    }.copy(fontWeight = FontWeight.SemiBold)
                    Text(
                        buildAnnotatedString { appendInline(block.text, styles) },
                        style = hStyle,
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                is Block.Code -> {
                    val codeText = if (isStreaming && isLast && !block.closed)
                        block.code + CURSOR else block.code
                    CodeBlockView(
                        code = codeText,
                        language = block.language,
                        onInsertToInput = onCodeInsert,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                is Block.Quote -> {
                    Row(Modifier.height(IntrinsicSize.Min).padding(vertical = 4.dp)) {
                        Box(
                            Modifier
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(styles.linkColor.copy(alpha = 0.6f))
                        )
                        Column(Modifier.padding(start = 10.dp)) {
                            block.lines.forEach { q ->
                                Text(
                                    buildAnnotatedString { appendInline(q, styles) },
                                    style = style.copy(
                                        fontStyle = FontStyle.Italic,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
                is Block.ListItem -> {
                    Row(Modifier.padding(start = (4 + block.indent * 16).dp, top = 2.dp)) {
                        Text(
                            text = if (block.marker.firstOrNull()?.isDigit() == true)
                                "${block.marker} " else "•  ",
                            style = style.copy(color = styles.linkColor)
                        )
                        Text(
                            buildAnnotatedString {
                                appendInline(block.text, styles)
                                if (isStreaming && isLast) {
                                    pushStyle(SpanStyle(color = styles.linkColor))
                                    append(CURSOR); pop()
                                }
                            },
                            style = style
                        )
                    }
                }
                Block.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                )
                is Block.TableFallback -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(ShapeCodeBlock)
                            .background(styles.codeBg)
                            .padding(10.dp)
                    ) {
                        block.lines.forEach { row ->
                            Text(
                                row,
                                style = style.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
