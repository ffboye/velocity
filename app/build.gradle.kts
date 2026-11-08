import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val signingPath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull
val updateConfig =
    Properties().apply { rootProject.file("update.properties").inputStream().use { load(it) } }

fun quoted(value: String) =
    "\"${value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "").replace("\r", "")}\""

val appVersion =
    Properties().apply { rootProject.file("version.properties").inputStream().use { load(it) } }

android {
    namespace = "com.example.myapplication"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdk = 24
        targetSdk = 37
        versionCode =
            appVersion.getProperty("versionCode").toInt().also { require(it in 1..2100000000) }
        versionName = appVersion.getProperty("versionName")
        buildConfigField("String", "UPDATE_OWNER", quoted(updateConfig.getProperty("UPDATE_OWNER")))
        buildConfigField(
            "String",
            "UPDATE_REPOSITORY",
            quoted(updateConfig.getProperty("UPDATE_REPOSITORY")),
        )
        buildConfigField(
            "String",
            "UPDATE_PUBLIC_KEY",
            quoted(rootProject.file("config/update-public-key.txt").readText().trim()),
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (signingPath != null) {
            create("distribution") {
                storeFile = file(signingPath)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }
    buildTypes {
        release {
            if (signingPath != null) signingConfig = signingConfigs.getByName("distribution")
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    testImplementation(libs.json)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
