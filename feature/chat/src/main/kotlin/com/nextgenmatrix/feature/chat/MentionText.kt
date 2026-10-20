package com.nextgenmatrix.feature.chat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import com.nextgenmatrix.core.model.RoomMemberBrief

private val mxidRegex = Regex("@([A-Za-z0-9._=/-]+:[A-Za-z0-9.-]+)")
private val roomRegex = Regex("(?<![\\w@])@room(?![\\w])", RegexOption.IGNORE_CASE)

/** `@user:server` is matched greedily; a trailing dot or dash belongs to the sentence instead. */
private fun MatchResult.trimmedMxid(): Pair<String, Int> {
    val trimmed = value.trimEnd('.', '-')
    return trimmed to (range.first + trimmed.length)
}

private fun nameFor(mxid: String, memberById: Map<String, RoomMemberBrief>): String =
    memberById[mxid]?.displayName?.takeIf { it.isNotBlank() }
        ?: mxid.substringAfter('@').substringBefore(':')

/** Plain "@name" text for places that cannot draw pills (reply bar, previews). */
internal fun mentionsToLabels(body: String, memberById: Map<String, RoomMemberBrief>): String {
    if (!body.contains('@')) return body
    val sb = StringBuilder()
    var i = 0
    for (m in mxidRegex.findAll(body)) {
        val (mxid, end) = m.trimmedMxid()
        sb.append(body, i, m.range.first).append('@').append(nameFor(mxid, memberById))
        i = end
    }
    sb.append(body, i, body.length)
    return sb.toString()
}

/** Message text with tagged people and @room drawn as highlighted names. */
internal fun mentionAnnotated(
    body: String,
    memberById: Map<String, RoomMemberBrief>,
    pillBackground: Color,
    pillColor: Color,
): AnnotatedString {
    val pill = SpanStyle(
        color = pillColor,
        fontWeight = FontWeight.SemiBold,
        background = pillBackground,
    )
    val hits = ArrayList<Triple<Int, Int, String>>()
    for (m in mxidRegex.findAll(body)) {
        val (mxid, end) = m.trimmedMxid()
        hits += Triple(m.range.first, end, "@" + nameFor(mxid, memberById))
    }
    for (m in roomRegex.findAll(body)) {
        hits += Triple(m.range.first, m.range.last + 1, "@room")
    }
    if (hits.isEmpty()) return AnnotatedString(body)
    hits.sortBy { it.first }
    return buildAnnotatedString {
        var i = 0
        for ((start, end, label) in hits) {
            if (start < i) continue
            append(body.substring(i, start))
            withStyle(pill) { append(label) }
            i = end
        }
        append(body.substring(i))
    }
}

/** Colours the "@name" labels the user tagged while typing. Offsets are unchanged. */
internal class MentionHighlightTransformation(
    private val labels: Set<String>,
    private val color: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val styled = buildAnnotatedString {
            append(text)
            val tokens = labels.map { "@$it" } + "@room"
            for (token in tokens) {
                var from = 0
                while (true) {
                    val at = raw.indexOf(token, from, ignoreCase = token == "@room")
                    if (at < 0) break
                    val before = raw.getOrNull(at - 1)
                    val after = raw.getOrNull(at + token.length)
                    val startOk = before == null || before.isWhitespace() || before in "([{"
                    val endOk = after == null || !(after.isLetterOrDigit() || after == '_')
                    if (startOk && endOk) {
                        addStyle(
                            SpanStyle(color = color, fontWeight = FontWeight.SemiBold),
                            at,
                            at + token.length,
                        )
                    }
                    from = at + token.length
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Finds the "@query" being typed just before [cursor], or null when none is open. */
internal fun mentionQueryAtCursor(text: String, cursor: Int): Pair<Int, String>? {
    val end = cursor.coerceIn(0, text.length)
    var i = end - 1
    while (i >= 0) {
        val c = text[i]
        if (c == '\n') return null
        if (c == '@') {
            val before = text.getOrNull(i - 1)
            if (before != null && !before.isWhitespace() && before !in "([{") return null
            val query = text.substring(i + 1, end)
            if (query.startsWith(' ')) return null
            return i to query
        }
        if (end - i > 40) return null
        i--
    }
    return null
}
