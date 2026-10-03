# Inline GLSL for Android Studio

Highlights GLSL inside C++ raw strings using GLSL Support's syntax highlighter:

```cpp
const char* shader = R"glsl(
#version 300 es
void main() { gl_Position = vec4(1.0); }
)glsl";

// language=glsl
const char* other = R"(
void main() { gl_Position = vec4(0.0); }
)";
```

The comment can precede the declaration or the literal. The nearest preceding comment
must be the marker, with no intervening semicolon, brace, or other string literal.
The delimiter `glsl` is case-sensitive; the comment marker is case-insensitive.
Encoding prefixes `u8`, `u`, `U`, and `L` are accepted. Ordinary quoted strings,
concatenated literals, user-defined suffixes, and incomplete raw literals are ignored.

## Install

In Android Studio, open Settings > Plugins > gear > Install Plugin from Disk,
choose `build/distributions/inlineglsl-0.1.0.zip`, and restart.
Keep **GLSL Support 1.25** by Jan Polák enabled (plugin ID `GLSL`).
This companion plugin does not bundle GLSL Support.

## Build on Windows

Targets the locally installed Android Studio Rabbit 1, build
`AI-262.9437.185.2621.16467767`. Its bundled JBR 25 is the build JDK.
No IntelliJ IDEA installation is required.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test buildPlugin --console=plain
```

Override installation paths if needed:

```powershell
.\gradlew.bat test buildPlugin '-PandroidStudioPath=D:/Android Studio' '-PglslPluginPath=D:/plugins/GLSL4Idea'
```

`runIde` launches a separate Gradle sandbox with the companion and GLSL Support.
The plugin supports the 262 build family only; retest against newer SDKs before
changing compatibility bounds.

## Implementation

The installed C++ PSI implements `PsiLanguageInjectionHost`, but its
`isValidHost()` method returns false unless a specifically named JetBrains C++
injector (`com.jetbrains.cidr.lang.OCMultiLiteralTempInjector`) is registered.
That injector is absent from this Android Studio distribution. A real PSI test
confirmed that ordinary and raw string literals are invalid injection hosts.

The companion therefore registers an `Annotator` for the `ObjectiveC` language
ID used by the bundled C/C++ parser. It identifies complete raw literals,
lexes their bodies with GLSL Support's registered syntax highlighter, and maps
token offsets back into the C++ source. Explicit theme-derived foregrounds keep
plain identifiers from retaining C++ string colors. Delimiters are untouched.

This provides **lexical syntax highlighting only**. It does not add GLSL
completion, semantic highlighting, diagnostics, formatting, or an injection UI.

Tests load the installed C++ parser and GLSL plugin, verify the annotator's
extension registration, and check actual token ranges and theme attributes for
both marker forms. Negative tests cover unmarked strings and marker leakage.

See JetBrains documentation for [annotators](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html)
and [local SDK dependencies](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html).
