import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.intellij.platform")
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
    testImplementation(libs.junit)

    // IntelliJ Platform Gradle Plugin Dependencies Extension - read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    intellijPlatform {
        local(providers.gradleProperty("androidStudioPath").orElse("C:/Program Files/Android/Android Studio"))
        bundledPlugin("com.intellij.cidr.lang")
        localPlugin(providers.gradleProperty("glslPluginPath").orElse(
            "${System.getenv("APPDATA")}/Google/AndroidStudio2026.2.1/plugins/GLSL4Idea"
        ))
        testFramework(TestFrameworkType.Platform)

    }
}

kotlin { jvmToolchain(25) }

intellijPlatform {
    pluginConfiguration {
        name = "Inline GLSL"
        version = "0.1.0"
        ideaVersion {
            sinceBuild = "262.9437"
            untilBuild = "262.*" // Only the locally tested Android Studio build family.
        }
    }
    buildSearchableOptions = false
}
