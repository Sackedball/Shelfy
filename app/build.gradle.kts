import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Release signing and version come from local.properties (never committed) or -P flags, so no secret is in the repo:
//   kirana.keystore=C:/path/to/release.jks   kirana.keystorePassword=...   kirana.keyAlias=...   kirana.keyPassword=...
//   kirana.versionCode=2   kirana.versionName=1.1
val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use { load(it) }
    }

fun setting(name: String): String? = (findProperty(name) as String?) ?: localProperties.getProperty(name)

android {
    namespace = "com.kirana.fefoguard"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.kirana.fefoguard"
        minSdk = 26
        targetSdk = 36
        versionCode = setting("kirana.versionCode")?.toInt() ?: 1
        versionName = setting("kirana.versionName") ?: "1.0"
        testInstrumentationRunner = "com.kirana.fefoguard.CustomTestRunner"
    }

    signingConfigs {
        val keystore = setting("kirana.keystore")
        if (keystore != null) {
            create("release") {
                storeFile = file(keystore)
                storePassword = setting("kirana.keystorePassword")
                keyAlias = setting("kirana.keyAlias")
                keyPassword = setting("kirana.keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Signed with the owner's key when one is configured. For a local smoke test only, -Pkirana.debugSignRelease=true
            // signs with the debug key so the minified build can be installed; never distribute that one.
            signingConfig =
                when {
                    signingConfigs.findByName("release") != null -> signingConfigs.getByName("release")
                    setting("kirana.debugSignRelease") == "true" -> signingConfigs.getByName("debug")
                    else -> null
                }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.zxing.core)
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    // Core Android dependencies
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // Arch Components
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Compose
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    // Tooling
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Instrumented tests
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Local tests: jUnit, coroutines, Android runner
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    // Instrumented tests: jUnit rules and runners
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    androidTestImplementation(libs.hilt.testing)
    kspAndroidTest(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)

    // DateTime
    implementation(libs.kotlinx.datetime)
}
