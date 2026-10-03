// Open this file in Android Studio after installing Inline GLSL.
const char* vertexShader = R"glsl(
#version 300 es
layout(location = 0) in vec3 position;
uniform mat4 transform;
void main() {
    gl_Position = transform * vec4(position, 1.0);
}
)glsl";

// language=glsl
const char* fragmentShader = R"(
#version 300 es
precision mediump float;
out vec4 color;
void main() {
    color = vec4(1.0, 0.5, 0.0, 1.0);
}
)";

const char* unmarked = R"(This remains a C++ string.)";

#define FOVEATION_GLSL R"(vec2 foveation(vec2 uv) { return uv; })"
// language=glsl
const char* splicedShader = R"(
#version 300 es
precision mediump float;
)" FOVEATION_GLSL R"(
out vec4 color;
void main() {
    color = vec4(foveation(vec2(0.5)), 0.0, 1.0);
}
)";

//language=glsl
#define MY_GLSL R"(#version 320 es
        precision mediump float;
        flat out int vIndex;
        void main() {
            vIndex = gl_VertexID;
        }
)"
