package com.remmel.inlineglsl

import com.intellij.codeInsight.daemon.impl.AnnotationHolderImpl
import com.intellij.lang.annotation.AnnotationSession
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.LanguageAnnotators
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.lang.Language
import com.intellij.openapi.editor.markup.TextAttributes
import java.awt.Font
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.jetbrains.cidr.lang.psi.OCLiteralExpression

class GlslRawStringAnnotatorTest : BasePlatformTestCase() {
    private fun checkDefine(prefix: String, delimiter: String = "", parameters: String = "") {
        val body = "#version 320 es\nprecision mediump float;\nflat out int vIndex;\nvoid main() { vIndex = gl_VertexID; }\n"
        val source = prefix + "#define MY_GLSL$parameters R\"$delimiter($body)$delimiter\"\n" +
            "#define OTHER R\"(unmarked macro body)\"\n"
        val file = myFixture.configureByText("shader.cpp", source)
        val defines = PsiTreeUtil.findChildrenOfType(file, com.jetbrains.cidr.lang.psi.OCDefineDirective::class.java)
            .sortedBy { it.textOffset }
        assertEquals(2, defines.size)
        val annotator = LanguageAnnotators.INSTANCE.allForLanguage(file.language)
            .filterIsInstance<GlslRawStringAnnotator>().single()
        val holder = AnnotationHolderImpl(AnnotationSession(file), false)
        holder.runAnnotatorWithContext(defines.first(), annotator)
        holder.assertAllAnnotationsCreated()
        if (prefix.isEmpty() && delimiter.isEmpty()) {
            assertTrue("Unmarked definitions should remain C++", holder.isEmpty())
        } else {
            assertFalse("GLSL inside a definition should be highlighted", holder.isEmpty())
            val bodyStart = source.indexOf(body)
            assertEquals(bodyStart, holder.first().startOffset)
            assertEquals(bodyStart + body.length, holder.last().endOffset)
            holder.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
            assertTrue("Only raw-string content should receive GLSL colors", holder.all {
                it.startOffset >= bodyStart && it.endOffset <= bodyStart + body.length
            })
            val keywordStart = source.indexOf("precision")
            assertTrue("IDE daemon must highlight macro definitions", myFixture.doHighlighting().any {
                it.startOffset == keywordStart && it.endOffset == keywordStart + "precision".length &&
                    it.forcedTextAttributes != null
            })
        }
        val other = AnnotationHolderImpl(AnnotationSession(file), false)
        other.runAnnotatorWithContext(defines.last(), annotator)
        assertTrue("Marker should not leak into the next macro", other.isEmpty())
    }

    fun testDefineWithCompactMarker() = checkDefine("//language=glsl\n")
    fun testDefineWithGlslDelimiter() = checkDefine("", "glsl")
    fun testFunctionLikeDefineWithMarker() = checkDefine("// language=glsl\n", parameters = "(argument)")
    fun testUnmarkedDefineIgnored() = checkDefine("")
    fun testMarkerDoesNotCrossEmptyDefine() {
        checkHighlighting("//language=glsl\n#define EMPTY\nauto plain = R\"(unmarked)\";", listOf(null))
    }

    private fun checkConcatenation(prefix: String, firstDelimiter: String = "", macro: Boolean = true) {
        val first = "precision mediump float;"
        val last = "void main() { float x = 1.0; }"
        val between = if (macro) " FOVEATION_GLSL " else " "
        val source = "#define FOVEATION_GLSL R\"(vec2 foveation(vec2 uv) { return uv; })\"\n" +
            prefix + "auto shader = R\"$firstDelimiter($first)$firstDelimiter\"" + between + "R\"($last)\";" +
            "\nauto plain = R\"(unrelated text)\";"
        val file = myFixture.configureByText("shader.cpp", source)
        val hosts = PsiTreeUtil.findChildrenOfType(file, OCLiteralExpression::class.java)
            .filter { it.isStringLiteral }.sortedBy { it.textOffset }
        assertEquals(2, hosts.size)
        val holder = AnnotationHolderImpl(AnnotationSession(file), false)
        holder.runAnnotatorWithContext(hosts.first(), GlslRawStringAnnotator())
        holder.assertAllAnnotationsCreated()
        val ranges = listOf(first, last).map { body ->
            val start = source.indexOf(body)
            com.intellij.openapi.util.TextRange(start, start + body.length)
        }
        if (prefix.isEmpty() && firstDelimiter.isEmpty()) {
            assertTrue("Unmarked concatenation must remain a C++ string", holder.isEmpty())
        } else {
            for (range in ranges) {
                val annotations = holder.filter { it.startOffset >= range.startOffset && it.endOffset <= range.endOffset }
                assertFalse("Each physical raw-string body must be highlighted", annotations.isEmpty())
                assertEquals(range.startOffset, annotations.first().startOffset)
                assertEquals(range.endOffset, annotations.last().endOffset)
                annotations.zipWithNext().forEach { (a, b) -> assertEquals(a.endOffset, b.startOffset) }
            }
            assertTrue("Macro names and raw delimiters must remain C++", holder.all { annotation ->
                ranges.any { annotation.startOffset >= it.startOffset && annotation.endOffset <= it.endOffset }
            })
            val numberStart = source.indexOf("1.0")
            assertTrue("Daemon must highlight the shader after the macro splice", myFixture.doHighlighting().any {
                it.startOffset == numberStart && it.endOffset == numberStart + 3 && it.forcedTextAttributes != null
            })
        }
        val plainHolder = AnnotationHolderImpl(AnnotationSession(file), false)
        plainHolder.runAnnotatorWithContext(hosts.last(), GlslRawStringAnnotator())
        assertTrue("Marker must not leak past the declaration", plainHolder.isEmpty())
    }

    fun testMacroSpliceWithComment() = checkConcatenation("// language=glsl\n")
    fun testMacroSpliceWithDelimiter() = checkConcatenation("", "glsl")
    fun testAdjacentRawStringsWithComment() = checkConcatenation("// language=glsl\n", macro = false)
    fun testUnmarkedMacroSpliceIgnored() = checkConcatenation("")

    private fun checkHighlighting(source: String, expected: List<String?>) {
        val file = myFixture.configureByText("shader.cpp", source)
        val hosts = PsiTreeUtil.findChildrenOfType(file, OCLiteralExpression::class.java)
            .filter { it.isStringLiteral }.sortedBy { it.textOffset }
        assertEquals("C++ parser must produce literal PSI", expected.size, hosts.size)
        hosts.zip(expected).forEach { (host, content) ->
            assertFalse("Android Studio gates C++ injection hosts", host.isValidHost)
            val annotator = LanguageAnnotators.INSTANCE.allForLanguage(host.language)
                .filterIsInstance<GlslRawStringAnnotator>().single()
            val holder = AnnotationHolderImpl(AnnotationSession(file), false)
            holder.runAnnotatorWithContext(host, annotator)
            holder.assertAllAnnotationsCreated()
            if (content == null) {
                assertTrue("Unexpected highlighting for ${host.text}", holder.isEmpty())
            } else {
                assertFalse("Expected GLSL highlighting for ${host.text}", holder.isEmpty())
                val raw = RawString.parse(host.text)!!
                val base = host.textOffset + raw.content.startOffset
                assertEquals(content, raw.content.substring(host.text))
                val highlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(
                    Language.findLanguageByID("GLSL")!!, project, file.virtualFile)!!
                val lexer = highlighter.highlightingLexer
                lexer.start(content)
                val scheme = EditorColorsManager.getInstance().globalScheme
                var i = 0
                while (lexer.tokenType != null) {
                    val annotation = holder[i++]
                    assertEquals(base + lexer.tokenStart, annotation.startOffset)
                    assertEquals(base + lexer.tokenEnd, annotation.endOffset)
                    assertEquals(HighlightSeverity.INFORMATION, annotation.severity)
                    var attributes = TextAttributes(scheme.defaultForeground, null, null, null, Font.PLAIN)
                    for (key in highlighter.getTokenHighlights(lexer.tokenType!!)) {
                        attributes = TextAttributes.merge(attributes, scheme.getAttributes(key))
                    }
                    assertEquals(attributes, annotation.enforcedTextAttributes)
                    lexer.advance()
                }
                assertEquals(i, holder.size)
            }
        }
    }

    fun testDelimiterAndMultilineOffsets() {
        val shader = "\n#version 300 es\nvoid main() { gl_Position = vec4(1.0); }\n"
        checkHighlighting("auto shader = R\"glsl($shader)glsl\";", listOf(shader))
    }

    fun testCommentBeforeDeclaration() {
        checkHighlighting("// language=glsl\nauto shader = R\"(void main() {})\";", listOf("void main() {}"))
    }

    fun testMarkerDoesNotLeakToNextDeclaration() {
        checkHighlighting("// language=glsl\nauto a = R\"(void main() {})\";\nauto b = R\"(plain text)\";",
            listOf("void main() {}", null))
    }

    fun testUnmarkedAndOrdinaryStringsIgnored() {
        checkHighlighting("auto a = R\"(plain text)\"; auto b = \"ordinary\";", listOf(null, null))
    }

    fun testPrefixAndCustomMarkedDelimiter() {
        checkHighlighting("// LANGUAGE = GLSL\nauto shader = u8R\"shader(void main() {})shader\";",
            listOf("void main() {}"))
    }

    fun testNearestUnrelatedCommentStopsMarker() {
        checkHighlighting("// language=glsl\n// documentation\nauto shader = R\"(void main() {})\";", listOf(null))
    }

    fun testMarkerDoesNotCrossBrace() {
        checkHighlighting("// language=glsl\nvoid f() { auto shader = R\"(void main() {})\"; }", listOf(null))
    }

    fun testRealDaemonHighlighting() {
        val shader = "void main() { float value = 1.0; /* shader comment */ }"
        val source = "auto shader = R\"glsl($shader)glsl\";"
        myFixture.configureByText("shader.cpp", source)
        val numberStart = source.indexOf("1.0")
        val highlights = myFixture.doHighlighting()
        assertTrue("Daemon must apply GLSL token annotations", highlights.any {
            it.startOffset == numberStart && it.endOffset == numberStart + 3 &&
                it.forcedTextAttributes != null
        })
    }

    fun testRawParserBoundaries() {
        assertNull(RawString.parse("R\"glsl(unfinished"))
        assertNull(RawString.parse("R\"glsl(a)glsl\" R\"glsl(b)glsl\""))
        assertNull(RawString.parse("R\"glsl(a)glsl\"_suffix"))
        assertNull(RawString.parse("R\"abcdefghijklmnopq(a)abcdefghijklmnopq\""))
        val raw = RawString.parse("LR\"glsl(a\\nb)glsl\"")!!
        assertEquals("a\\nb", raw.content.substring("LR\"glsl(a\\nb)glsl\""))
    }
}

