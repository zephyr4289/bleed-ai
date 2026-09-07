# 🧩 Phase 6 — Component Library

**Goal:** Every reusable visual unit — code blocks, thinking panel, citations, the markdown engine, and the message bubble — plus a **component gallery screen** so you can actually *see* the design system on your phone after CI builds (since we have no IDE/previews in the Termux flow). **Gate:** CI green → install APK → gallery renders correctly.

**Two structural decisions up front (deviations from the PDF, both deliberate):**

1. **`MarkdownRenderer` is custom-built, not mikepenz.** Reasons: zero new dependency risk (CI is our only compiler — a wrong artifact coordinate costs a debug round-trip), full streaming control (chat re-parses every chunk), and the renderer API is one function, so swapping to a library later is a single-file change if you ever want full GFM tables. Coverage: **fenced code → headers, bold/italic/inline-code, links + auto-linked bare URLs, nested lists, blockquotes, rules**. Tables degrade to a monospace box (honest fallback; noted in code).
2. **`StreamingCursor.kt` and `MessageActionToolbar.kt` are dropped.** The cursor becomes an inline `▍` glyph appended to the streaming text (PDF's Row-float version breaks under text wrap — it sat awkwardly beside the whole paragraph). The toolbar logic stays inlined in `MessageBubble`, same as the PDF effectively did.

No new dependencies. `libs.versions.toml` untouched.

## Setup

```bash
cd ~/zai
mkdir -p app/src/main/java/com/zai/chat/ui/{components,debug}
```

---

## File 1: `ui/components/CodeBlockView.kt`

```kotlin
package com.zai.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.WrapText
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.ShapeCodeBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fenced-code renderer: language chip, copy-with-feedback, wrap toggle.
 * Theme-aware (P0 tokens) — light and dark both correct, unlike the PDF's
 * hardcoded dark constants.
 */
@Composable
fun CodeBlockView(
    code: String,
    language: String = "",
    onInsertToInput: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isCopied by remember { mutableStateOf(false) }
    var wrapLines by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), ShapeCodeBlock)
            .background(MaterialTheme.colorScheme.surfaceVariant, ShapeCodeBlock)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, ShapeCodeBlock)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "text" }.lowercase(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { wrapLines = !wrapLines }, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.WrapText,
                        contentDescription = "Toggle wrap",
                        tint = if (wrapLines) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        isCopied = true
                        scope.launch { delay(2000); isCopied = false }
                    },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (isCopied) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        // Body
        val scrollState = rememberScrollState()
        val textModifier = if (wrapLines) {
            Modifier.fillMaxWidth().padding(12.dp)
        } else {
            Modifier.horizontalScroll(scrollState).padding(12.dp)
        }
        Text(
            text = code,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = textModifier
        )
    }
}
```

## File 2: `ui/components/ThinkingBlock.kt`

```kotlin
package com.zai.chat.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.ui.theme.ShapeThinking
import com.zai.chat.ui.theme.ZaiMotion

/**
 * Collapsible reasoning panel — the app's signature component.
 *
 * Fixes vs PDF:
 *  - Expansion is driven by LaunchedEffect (the PDF wrote state during
 *    composition, which works by accident and breaks under recomposition).
 *  - Fully theme-aware (PDF hardcoded dark colors → broken in light mode).
 */
@Composable
fun ThinkingBlock(
    reasoningText: String,
    isStreamingReasoning: Boolean,
    durationSeconds: Float = 0f,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(isStreamingReasoning) }

    // Auto-expand while reasoning streams; user may collapse mid-stream.
    LaunchedEffect(isStreamingReasoning) {
        if (isStreamingReasoning) expanded = true
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                ShapeThinking
            )
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                ShapeThinking
            )
            .animateContentSize(animationSpec = ZaiMotion.PanelExpandSpring)
    ) {
        // Header bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (isStreamingReasoning) {
                ThinkingShimmerText(text = "Thinking deeply…")
            } else {
                Text(
                    text = if (durationSeconds > 0f)
                        "Thought for ${"%.1f".format(durationSeconds)}s"
                    else "Thought process",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp
                              else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
        // Scrollable monospace body
        AnimatedVisibility(visible = expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
            ) {
                Text(
                    text = reasoningText.ifEmpty { "Generating thoughts…" },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = FontStyle.Italic,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.9f)
                    ),
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

/** Peach shimmer sweep across the "Thinking deeply…" label. */
@Composable
fun ThinkingShimmerText(text: String) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )
    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            MaterialTheme.colorScheme.primary.copy(alpha = 1.0f),
            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
        ),
        start = Offset(translateAnim - 200f, 0f),
        end = Offset(translateAnim, 0f)
    )
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(
            fontWeight = FontWeight.SemiBold,
            brush = brush
        )
    )
}
```

## File 3: `ui/components/WebCitationStrip.kt`

```kotlin
package com.zai.chat.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.chat.data.model.SearchCitation
import com.zai.chat.ui.theme.KimiCyan

/** Horizontal scrollable source chips; tap opens the browser. */
@Composable
fun WebCitationStrip(
    citations: List<SearchCitation>,
    modifier: Modifier = Modifier
) {
    if (citations.isEmpty()) return
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = KimiCyan,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Sources (${citations.size})",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            citations.forEach { citation ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(citation.url))
                                )
                            }
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "[${citation.index}]",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = KimiCyan,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = citation.title.ifBlank { citation.url },
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(130.dp)
                    )
                }
            }
        }
    }
}
```

## File 4: `ui/components/MarkdownRenderer.kt` — the engine


```kotlin
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyle
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
    dat
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
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyle
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
            line.trimStart().startsWith("|") && line.contains("|", 2) -> {
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
        when {
            g[1].isNotEmpty() -> pushStyle(
                SpanStyle(fontFamily = s.mono, background = s.codeBg, fontSize = 14.sp)
            ).also { append(g[1]); pop() }
            g[2].isNotEmpty() -> {
                pushLink(
                    LinkAnnotation.Url(
                        g[3],
                        TextLinkStyle(
                            SpanStyle(color = s.linkColor, textDecoration = TextDecoration.Underline)
                        )
                    )
                )
                append(g[2]); pop()
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
            pushLink(
                LinkAnnotation.Url(
                    u.value,
                    TextLinkStyle(
                        SpanStyle(color = s.linkColor, textDecoration = TextDecoration.Underline)
                    )
                )
            )
            append(u.value); pop()
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




package com.zai.chat.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.ui.theme.ShapeBubbleUser
import com.zai.chat.ui.theme.UserBubbleFillDark

/**
 * One message. User = right pill (plain text — input is literal).
 * Assistant = edge-to-edge document flow: Thinking → Markdown → Citations
 * → partial-retry, with tap/long-press action reveal.
 *
 * Contracts with P7:
 *  - onRegenerate = "regenerate THIS assistant message" (delete onward + restream)
 *  - onEditAndResend = message content goes into P7's edit dialog first
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isStreaming: Boolean,
    onEditAndResend: ((Message) -> Unit)? = null,
    onRegenerate: (() -> Unit)? = null,
    onRetryFromHere: ((Message) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showActions by remember { mutableStateOf(false) }
    val isUser = message.role == MessageRole.USER

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                shape = ShapeBubbleUser,
                color = UserBubbleFillDark,
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .combinedClickable(
                        onClick = { showActions = !showActions },
                        onLongClick = { showActions = true }
                    )
            ) {
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { showActions = !showActions },
                        onLongClick = { showActions = true }
                    )
            ) {
                // 1. Thinking (also when content hasn't started yet)
                if (!message.reasoning.isNullOrEmpty()) {
                    ThinkingBlock(
                        reasoningText = message.reasoning,
                        isStreamingReasoning = isStreaming && message.content.isEmpty(),
                        modifier = Modifier.padding(bottom = 10.dp)
                    )
                }
                // 2. Document body (selectable) — empty while only thinking
                if (message.content.isNotEmpty()) {
                    SelectionContainer {
                        MarkdownText(markdown = message.content, isStreaming = isStreaming)
                    }
                } else if (!isStreaming && message.reasoning.isNullOrEmpty()) {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // 3. Citations
                if (message.citations.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    WebCitationStrip(citations = message.citations)
                }
                // 4. Inline retry on interrupted stream
                if (message.isPartial && !isStreaming) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { onRetryFromHere?.invoke(message) },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Stream interrupted — tap to resume")
                    }
                }
            }
        }

        // Action reveal
        AnimatedVisibility(visible = showActions, enter = fadeIn() + scaleIn()) {
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = {
                        val clipboard =
                            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Message", message.content)
                        )
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        showActions = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, message.content)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share"))
                        showActions = false
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                if (isUser && onEditAndResend != null) {
                    IconButton(
                        onClick = { onEditAndResend(message); showActions = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit and resend",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else if (!isUser && !isStreaming && onRegenerate != null) {
                    IconButton(
                        onClick = { onRegenerate(); showActions = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Regenerate",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}


package com.zai.chat.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zai.chat.data.model.Message
import com.zai.chat.data.model.MessageRole
import com.zai.chat.data.model.SearchCitation
import com.zai.chat.ui.components.CodeBlockView
import com.zai.chat.ui.components.MarkdownText
import com.zai.chat.ui.components.MessageBubble
import com.zai.chat.ui.components.ThinkingBlock
import com.zai.chat.ui.components.WebCitationStrip

private val SectionShape = RoundedCornerShape(10.dp)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), SectionShape)
            .background(MaterialTheme.colorScheme.surface, SectionShape)
            .padding(12.dp)
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(Modifier.height(8.dp))
        content()
    }
}

/** Visual regression target for the component library — view on-device. */
@Composable
fun ComponentGalleryScreen(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
    ) {
        Text("Component Gallery", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        Section("ThinkingBlock — done") {
            ThinkingBlock(reasoningText = "User wants X. Consider Y… then Z.", isStreamingReasoning = false, durationSeconds = 4.2f)
        }
        Section("ThinkingBlock — streaming") {
            ThinkingBlock(reasoningText = "Parsing the request… checking edge…", isStreamingReasoning = true)
        }
        Section("CodeBlockView") {
            CodeBlockView(
                language = "kotlin",
                code = "fun fib(n: Int): Long =\n    if (n < 2) n.toLong() else fib(n - 1) + fib(n - 2)"
            )
        }
        Section("WebCitationStrip") {
            WebCitationStrip(
                listOf(
                    SearchCitation(1, "Kotlin coroutines guide", "https://kotlinlang.org/docs/coroutines-guide.html"),
                    SearchCitation(2, "Room persistence", "https://developer.android.com/training/data-storage/room")
                )
            )
        }
        Section("MarkdownText — full syntax") {
            MarkdownText(
                markdown = """
                    ## Heading 2
                    **Bold**, *italic*, `inline code`, and a [link](https://kotlinlang.org).
                    Bare URL too: https://developer.android.com

                    - Top level item
                      - Nested item
                        - Deeper still
                    1. First
                    2. Second

                    > A quote that wraps across
                    > multiple source lines.

                    ---
                    | a | b |
                    |---|---|
                    | 1 | 2 |

                    Final paragraph after a rule.
                """.trimIndent()
            )
        }
        Section("MarkdownText — streaming") {
            MarkdownText(
                markdown = "Writing a **streaming** response with an unterminated fence:\n\n```python\ndef hello():",
                isStreaming = true
            )
        }
        Section("MessageBubble — user") {
            MessageBubble(
                message = Message("u1", "c1", MessageRole.USER, "Explain Room FTS vs FTS4 tradeoffs?"),
                isStreaming = false
            )
        }
        Section("MessageBubble — assistant complete") {
            MessageBubble(
                message = Message(
                    "a1", "c1", MessageRole.ASSISTANT,
                    content = "FTS4 with a **content entity** avoids duplicating text — the index mirrors `messages` via triggers.\n\nKey win: one write path.",
                    reasoning = "Comparing FTS3/4/5… contentEntity avoids double storage…",
                    citations = listOf(
                        SearchCitation(1, "Room FTS docs", "https://developer.android.com")
                    )
                ),
                isStreaming = false
            )
        }
        Section("MessageBubble — partial (retry)") {
            MessageBubble(
                message = Message(
                    "a2", "c1", MessageRole.ASSISTANT,
                    content = "The stream dropped mid-sent",
                    isPartial = true
                ),
                isStreaming = false,
                onRetryFromHere = { }
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

// inside setContent { ZaiTheme { ... } }, before the auth check:
var showGallery by remember { mutableStateOf(false) }

// then wrap the existing gate/home logic:
if (showGallery) {
    ComponentGalleryScreen(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 32.dp)
    )
    // floating close
    androidx.compose.material3.TextButton(
        onClick = { showGallery = false },
        modifier = Modifier.padding(16.dp)
    ) { Text("← Back") }
} else if (showAuth) {
    // ... existing TokenReconnectScreen branch unchanged ...
} else {
    // ... existing PlaceholderHome, plus one new control:
    // (add inside PlaceholderHome's Column, after the "Manage session" button:)
    // TextButton(onClick = onOpenGallery) { Text("Component gallery") }
}


Red? Ranked suspects:

LinkAnnotation/TextLinkStyle/pushLink — the one API I can't fully verify against BOM 2024.09.00 from memory. If CI errors there: in appendInline, replace each pushLink(LinkAnnotation.Url(url, TextLinkStyle(style))) { … } pop() pair with pushStyle(style); append(label); pop() — links lose click-to-open (citations strip still opens real URLs), everything else unaffected. Paste me the error and I'll patch exactly.
Regex escaping mangled by nano — every \\*, \\[ must survive; check character counts.
IntrinsicSize.Min import (androidx.compose.foundation.layout.IntrinsicSize).
