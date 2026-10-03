# Inline GLSL for Android Studio

Highlights GLSL in C++ raw strings, including macro definitions and concatenated strings.
Requires **GLSL Support 1.25** by Jan Polák. Tested on Android Studio Rabbit 1 (2026.2.1, build 262).

Add `//language=glsl` before each declaration or definition:

```cpp
//language=glsl
#define SHADER_UNIFORMS R"(uniform vec4 color;)"

//language=glsl
const char* fragmentShader = R"(
#version 300 es
precision mediump float;
)" SHADER_UNIFORMS R"(
out vec4 fragColor;

void main() {
    fragColor = color;
}
)";
```

Alternatively, use `R"glsl(...)glsl"` without a comment.
Macro concatenation requires Android Studio to resolve the macro.
Syntax highlighting only; no GLSL completion or diagnostics.

## Install

**Settings → Plugins → ⚙ → Install Plugin from Disk**, select
`build/distributions/inlineglsl-0.1.2.zip`, then restart.
Keep GLSL Support enabled.

## Build on Windows

Uses your local Android Studio installation; IntelliJ IDEA is not required.

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat test buildPlugin
```

For custom paths, add `-PandroidStudioPath="D:/Android Studio"` and
`-PglslPluginPath="D:/plugins/GLSL4Idea"`.
