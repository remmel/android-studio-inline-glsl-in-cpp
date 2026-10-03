package com.remmel.inlineglsl

import com.intellij.lang.Language
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.cidr.lang.psi.OCLiteralExpression
import com.jetbrains.cidr.lang.psi.OCDefineDirective
import com.jetbrains.cidr.lang.parser.OCLexerTokenTypes
import com.jetbrains.cidr.lang.preprocessor.OCMacroForeignLeafElement
import java.awt.Font

/** Lexical highlighting only: Android Studio gates C++ injection hosts on an absent plugin. */
class GlslRawStringAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val host = when (element) {
            is OCLiteralExpression -> element.takeIf { it.isStringLiteral } ?: return
            is OCDefineDirective -> element
            else -> return
        }
        // C++ represents adjacent strings and intervening macro expansions as one host.
        // Expansion leaves have synthetic ranges, so only annotate physical source tokens.
        val fragments = host.node.getChildren(OCLexerTokenTypes.ALL_STRINGS)
            .filterNot { it is OCMacroForeignLeafElement }
            .mapNotNull { node -> RawString.parse(node.text)?.let { node to it } }
        if (fragments.isEmpty()) return
        if (fragments.none { (_, raw) -> raw.delimiter == "glsl" } && !hasMarker(host)) return
        val language = Language.findLanguageByID("GLSL") ?: return
        val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(language, host.project, host.containingFile.virtualFile)
            ?: return
        val scheme = EditorColorsManager.getInstance().globalScheme
        // Explicit foreground prevents tokens with default colors inheriting C++ string green.
        val plain = TextAttributes(scheme.defaultForeground, null, null, null, Font.PLAIN)
        for ((node, raw) in fragments) {
            val body = raw.content.substring(node.text)
            val base = node.startOffset + raw.content.startOffset
            val lexer = highlighter.highlightingLexer
            lexer.start(body)
            while (lexer.tokenType != null) {
                ProgressManager.checkCanceled()
                val range = TextRange(base + lexer.tokenStart, base + lexer.tokenEnd)
                var attributes = plain
                for (key in highlighter.getTokenHighlights(lexer.tokenType!!)) {
                    attributes = TextAttributes.merge(attributes, scheme.getAttributes(key))
                }
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(range).enforcedTextAttributes(attributes).create()
                lexer.advance()
            }
        }
    }

    private fun hasMarker(host: PsiElement): Boolean {
        var leaf = PsiTreeUtil.prevLeaf(host)
        while (leaf != null) {
            val comment = PsiTreeUtil.getParentOfType(leaf, PsiComment::class.java, false)
            if (comment != null) return MARKER.matches(comment.text.trim())
            // A marker belongs to one declaration/directive, even when a macro has no strings.
            if (PsiTreeUtil.getParentOfType(leaf, OCDefineDirective::class.java, false) != null) return false
            val text = leaf.text
            // Stay within this expression; another literal cannot inherit a marker.
            if (text.startsWith("#") || text.any { it == ';' || it == '{' || it == '}' || it == '"' || it == '\'' }) return false
            leaf = PsiTreeUtil.prevLeaf(leaf)
        }
        return false
    }

    companion object {
        private val MARKER = Regex("//\\s*language\\s*=\\s*glsl\\s*", RegexOption.IGNORE_CASE)
    }
}

internal data class RawString(val delimiter: String, val content: TextRange) {
    companion object {
        private val OPEN = Regex("""(?:u8|u|U|L)?R"([^ ()\\\t\r\n]{0,16})\(""")

        fun parse(text: String): RawString? {
            val match = OPEN.find(text)?.takeIf { it.range.first == 0 } ?: return null
            val delimiter = match.groupValues[1]
            val closing = ")$delimiter\""
            val start = match.range.last + 1
            val end = text.indexOf(closing, start)
            if (end < start || end + closing.length != text.length) return null
            return RawString(delimiter, TextRange(start, end))
        }
    }
}
