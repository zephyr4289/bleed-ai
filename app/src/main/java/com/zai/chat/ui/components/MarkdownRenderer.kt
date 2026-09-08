package com.zai.chat.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.ReportProblem
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
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
import com.zai.chat.ui.theme.BorderAmbient
import com.zai.chat.ui.theme.CrimsonFlare
import com.zai.chat.ui.theme.EmeraldPulse
import com.zai.chat.ui.theme.QuantumCyan
import com.zai.chat.ui.theme.RadiantAmber
import com.zai.chat.ui.theme.SurfaceActive
import com.zai.chat.ui.theme.SurfaceBase
import com.zai.chat.ui.theme.SurfaceRaised
import com.zai.chat.ui.theme.SyntaxBackground
import com.zai.chat.ui.theme.TextPrimary
import com.zai.chat.ui.theme.TextSecondary
import com.zai.chat.ui.theme.TextTertiary

/*
 * ── BLOCK MODEL ────────────────────────────────────────────────────────
 * Line-based block parser: fences, headings, quotes/callouts, lists, rules, tables, paragraphs.
 */
private sealed class Block {
    data class Paragraph(val text: String) : Block()
    data class Heading(val level: Int, val text: String) : Block()
    data class Code(val language: String, val code: String, val closed: Boolean) : Block()
    data class Callout(val type: CalloutType, val title: String, val lines: List<String>) : Block()
    data class Quote(val lines: List<String>) : Block()
    data class ListItem(val indent: Int, val marker: String, val text: String) : Block()
    data object Rule : Block()
    data class TableFallback(val lines: List<String>) : Block()
}

private enum class CalloutType(val color: Color, val defaultTitle: String) {
    NOTE(QuantumCyan, "NOTE"),
    TIP(EmeraldPulse, "TIP"),
    IMPORTANT(RadiantAmber, "IMPORTANT"),
    WARNING(RadiantAmber, "WARNING"),
    CAUTION(CrimsonFlare, "CAUTION")
}

private val listItemRegex = Regex("""^(\s*)([-*+]|\d+\.)\s+(.*)$""")
private val hrRegex = Regex("""^\s*([-*_])\s*(\1\s*){2,}$""")
private val fenceRegex = Regex("""^\s*```\s*(\S*)""")
private val calloutHeaderRegex = Regex("""^\[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\](?:\s+(.*))?$""", RegexOption.IGNORE_CASE)

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
                blocks += Block.Code(language, code.toString().trimEnd('\n'), closed)
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
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoteLines += lines[i].trimStart().removePrefix(">").removePrefix(" ").trimStart()
                    i++
                }
                // Check if first line is a callout header e.g. [!NOTE]
                val firstLine = quoteLines.firstOrNull().orEmpty().trim()
                val calloutMatch = calloutHeaderRegex.find(firstLine)
                if (calloutMatch != null) {
                    val rawType = calloutMatch.groupValues[1].uppercase()
                    val cType = when (rawType) {
                        "TIP" -> CalloutType.TIP
                        "IMPORTANT" -> CalloutType.IMPORTANT
                        "WARNING" -> CalloutType.WARNING
                        "CAUTION" -> CalloutType.CAUTION
                        else -> CalloutType.NOTE
                    }
                    val customTitle = calloutMatch.groupValues[2].ifBlank { cType.defaultTitle }
                    blocks += Block.Callout(cType, customTitle, quoteLines.drop(1))
                } else {
                    blocks += Block.Quote(quoteLines)
                }
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
 */
private val inlineRegex = Regex(
    "`([^`\\n]+?)`" +
    "|\\[([^]\\n]+)]\\((https?://[^)\\s]+)\\)" +
    "|\\*\\*(.+?)\\*\\*" +
    "|__(.+?)__" +
    "|(?<![*\\w])\\*([^*\\n]+?)\\*(?![*\\w])" +
    "|(?<![_\\w])_([^_\\n]+?)_(?![\\w_])"
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
        when {
            g[1].isNotEmpty() -> pushStyle(
                SpanStyle(
                    fontFamily = s.mono,
                    background = s.codeBg,
                    color = s.linkColor,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp
                )
            ).also { append(" ${g[1]} "); pop() }
            g[2].isNotEmpty() -> {
                pushStyle(SpanStyle(color = s.linkColor, textDecoration = TextDecoration.Underline))
                append(g[2])
                pop()
            }
            g[4].isNotEmpty() -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary))
                .also { appendInline(g[4], s); pop() }
            g[5].isNotEmpty() -> pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = TextPrimary))
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
 * Editorial Markdown renderer with syntax highlighting, obsidian callouts, and specular tables.
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
        codeBg = SurfaceActive,
        linkColor = QuantumCyan,
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
                            pushStyle(SpanStyle(color = QuantumCyan, fontWeight = FontWeight.Bold))
                            append(CURSOR)
                            pop()
                        }
                    }
                    Text(annotated, style = style.copy(color = TextPrimary))
                    Spacer(Modifier.height(6.dp))
                }
                is Block.Heading -> {
                    val (hStyle, topPad) = when (block.level) {
                        1 -> MaterialTheme.typography.headlineMedium to 14.dp
                        2 -> MaterialTheme.typography.titleLarge to 12.dp
                        3 -> MaterialTheme.typography.titleMedium to 8.dp
                        else -> MaterialTheme.typography.titleSmall to 6.dp
                    }
                    Text(
                        buildAnnotatedString { appendInline(block.text, styles) },
                        style = hStyle.copy(fontWeight = FontWeight.Bold, color = TextPrimary),
                        modifier = Modifier.padding(top = topPad, bottom = 4.dp)
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
                is Block.Callout -> {
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(shape)
                            .background(block.type.color.copy(alpha = 0.08f))
                            .specularBorder(shape)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val icon = when (block.type) {
                                CalloutType.TIP -> Icons.Rounded.Lightbulb
                                CalloutType.IMPORTANT -> Icons.Rounded.ReportProblem
                                CalloutType.WARNING -> Icons.Rounded.Warning
                                CalloutType.CAUTION -> Icons.Rounded.Warning
                                CalloutType.NOTE -> Icons.Rounded.Info
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = block.type.color,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = block.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = block.type.color
                                )
                            )
                        }
                        if (block.lines.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            block.lines.forEach { line ->
                                Text(
                                    buildAnnotatedString { appendInline(line, styles) },
                                    style = style.copy(fontSize = 14.sp, color = TextSecondary)
                                )
                            }
                        }
                    }
                }
                is Block.Quote -> {
                    Row(
                        Modifier
                            .height(IntrinsicSize.Min)
                            .padding(vertical = 4.dp)
                    ) {
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
                                        color = TextSecondary
                                    )
                                )
                            }
                        }
                    }
                }
                is Block.ListItem -> {
                    Row(Modifier.padding(start = (4 + block.indent * 16).dp, top = 2.dp, bottom = 2.dp)) {
                        Text(
                            text = if (block.marker.firstOrNull()?.isDigit() == true)
                                "${block.marker} " else "•  ",
                            style = style.copy(color = QuantumCyan, fontWeight = FontWeight.Bold)
                        )
                        Text(
                            buildAnnotatedString {
                                appendInline(block.text, styles)
                                if (isStreaming && isLast) {
                                    pushStyle(SpanStyle(color = QuantumCyan, fontWeight = FontWeight.Bold))
                                    append(CURSOR)
                                    pop()
                                }
                            },
                            style = style.copy(color = TextPrimary)
                        )
                    }
                }
                Block.Rule -> Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .height(1.dp)
                        .background(BorderAmbient)
                )
                is Block.TableFallback -> {
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(shape)
                            .background(SyntaxBackground)
                            .specularBorder(shape)
                            .padding(12.dp)
                    ) {
                        block.lines.forEachIndexed { rowIdx, row ->
                            Text(
                                row,
                                style = style.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = if (rowIdx == 0) TextPrimary else TextSecondary,
                                    fontWeight = if (rowIdx == 0) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
