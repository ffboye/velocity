// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val ktfmt =
    configurations.create("ktfmt") {
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
            attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
            attribute(
                org.gradle.api.attributes.java.TargetJvmEnvironment
                    .TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
                objects.named(org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM),
            )
        }
    }

dependencies { add(ktfmt.name, "com.facebook:ktfmt:0.54") }

val kotlinSources =
    fileTree(rootDir) {
        include("**/*.kt", "**/*.gradle.kts")
        exclude("**/build/**", ".gradle/**", ".idea/**")
    }

tasks.register<JavaExec>("formatKotlin") {
    classpath = ktfmt
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style")
    args(kotlinSources.files.map { it.absolutePath })
}

tasks.register<JavaExec>("checkKotlinFormat") {
    classpath = ktfmt
    mainClass.set("com.facebook.ktfmt.cli.Main")
    args("--kotlinlang-style", "--dry-run", "--set-exit-if-changed")
    args(kotlinSources.files.map { it.absolutePath })
}
