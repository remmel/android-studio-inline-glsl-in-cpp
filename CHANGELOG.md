# Changelog

## 0.1.2

- Highlight GLSL raw strings inside object-like and function-like `#define` directives.
- Recognize a preceding `//language=glsl` marker or a `glsl` raw-string delimiter.
- Prevent comment markers from crossing earlier macro definitions.
- Test the supplied multiline macro example through the IDE highlighting daemon.

## 0.1.1

- Highlight all physical raw-string portions of a concatenated C++ literal,
  including `R"(...)" FOVEATION_GLSL R"(...)"` when the macro is resolved.
- Skip synthetic macro-expansion ranges and preserve C++ highlighting on macro names.
- Add regression tests for macro splices, adjacent raw strings, and IDE daemon highlighting.

## 0.1.0

- Highlight GLSL in C++ `R"glsl(...)glsl"` strings and raw strings preceded by
  `// language=glsl`.
- Use installed GLSL Support lexer and color settings.
- Target Android Studio Rabbit 1 build 262 with its local SDK and JBR 25.
