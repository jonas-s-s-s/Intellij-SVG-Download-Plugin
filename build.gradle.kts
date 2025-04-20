import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    // Core language support
    java
    alias(libs.plugins.kotlin)

    // JetBrains tools: IntelliJ Platform, Changelog, Code Analysis, Coverage
    alias(libs.plugins.intelliJPlatform)
    alias(libs.plugins.changelog)
    alias(libs.plugins.qodana)
    alias(libs.plugins.kover)
}

// Project coordinates
group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

// Kotlin JVM toolchain
kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    // Testing frameworks
    testImplementation(libs.junit)
    testImplementation(libs.opentest4j)

    // HTML parsing
    implementation("org.jsoup:jsoup:1.16.1")
    // SVG rendering (exclude duplicate XML APIs)
    implementation("org.apache.xmlgraphics:batik-all:1.17") {
        exclude("xml-apis", "xml-apis")
    }
    implementation("com.twelvemonkeys.imageio:imageio-batik:3.10.1")

    // IntelliJ Platform dependencies
    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion")
        )
        bundledPlugins(
            providers.gradleProperty("platformBundledPlugins").map { it.split(',') }
        )
        plugins(
            providers.gradleProperty("platformPlugins").map { it.split(',') }
        )
        testFramework(TestFrameworkType.Platform)
    }
}

intellijPlatform {
    pluginConfiguration {
        // Plugin metadata
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        // Extract description from README.md
        description = providers.fileContents(layout.projectDirectory.file("README.md")).asText.map { content ->
            val start = "<!-- Plugin description -->"
            val end = "<!-- Plugin description end -->"
            content.lines().run {
                require(containsAll(listOf(start, end))) { "Missing description section in README.md" }
                subList(indexOf(start) + 1, indexOf(end)).joinToString("\n").let(::markdownToHTML)
            }
        }

        // Render change notes as HTML for this version
        val changelog = project.changelog
        // Get the latest available change notes from the changelog file
        changeNotes = providers.gradleProperty("pluginVersion").map { pluginVersion ->
            with(changelog) {
                renderItem(
                    (getOrNull(pluginVersion) ?: getUnreleased())
                        .withHeader(false)
                        .withEmptySections(false),
                    Changelog.OutputType.HTML,
                )
            }
        }

        // IDE compatibility range
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        // Signing credentials
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        // Marketplace token
        token = providers.environmentVariable("PUBLISH_TOKEN")
        // Determine release channel from version
        channels = providers.gradleProperty("pluginVersion").map { ver ->
            listOf(ver.substringAfter('-', "").substringBefore('.').ifEmpty { "default" })
        }
    }

    // Verify against recommended IDE versions
    pluginVerification {
        ides { recommended() }
    }
}

// Changelog plugin: no custom groups
changelog {
    groups.set(emptyList())
    repositoryUrl.set(providers.gradleProperty("pluginRepositoryUrl"))
}

// Consistent Gradle wrapper version
tasks.wrapper {
    gradleVersion = providers.gradleProperty("gradleVersion").get()
}

// Ensure changelog is patched before publishing
tasks.publishPlugin {
    dependsOn(tasks.patchChangelog)
}
