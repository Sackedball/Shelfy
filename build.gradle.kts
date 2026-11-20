plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
// Pinned build-only formatter; no runtime dependency is added to the application.
val ktlint by configurations.creating {
    attributes {
        attribute(
            org.gradle.api.attributes.Bundling.BUNDLING_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.Bundling.SHADOWED),
        )
    }
}
dependencies { ktlint("com.pinterest.ktlint:ktlint-cli:1.7.1") }
for (action in listOf("Check", "Format")) {
    tasks.register<JavaExec>("ktlint$action") {
        group = "verification"
        description = "Run ktlint on project Kotlin sources and Gradle scripts"
        classpath = ktlint
        mainClass.set("com.pinterest.ktlint.Main")
        jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
        args("app/src/**/*.kt", "*.gradle.kts", "app/*.gradle.kts")
        if (action == "Format") args("--format")
    }
}
