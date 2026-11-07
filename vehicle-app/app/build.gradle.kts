plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val releaseSigningValues = mapOf(
    "storeFile" to System.getenv("MOAGIL_RELEASE_STORE_FILE").orEmpty().trim(),
    "storePassword" to System.getenv("MOAGIL_RELEASE_STORE_PASSWORD").orEmpty(),
    "keyAlias" to System.getenv("MOAGIL_RELEASE_KEY_ALIAS").orEmpty().trim(),
    "keyPassword" to System.getenv("MOAGIL_RELEASE_KEY_PASSWORD").orEmpty(),
)
val releaseSigningConfigured = releaseSigningValues.values.any(String::isNotEmpty)
if (releaseSigningConfigured && releaseSigningValues.values.any(String::isEmpty)) {
    error("Complete all MOAGIL_RELEASE_* signing environment variables to sign the release build.")
}

val kakaoMapJavascriptKey = System.getenv("MOAGIL_KAKAO_JAVASCRIPT_KEY").orEmpty().trim().ifBlank {
    rootProject.file("../.env").takeIf { it.isFile }?.useLines { lines ->
        lines.map(String::trim)
            .firstOrNull { it.startsWith("MOAGIL_KAKAO_JAVASCRIPT_KEY=") }
            ?.substringAfter('=')
            ?.trim()
            ?.removeSurrounding("\"")
            ?.removeSurrounding("'")
    }.orEmpty()
}
val escapedKakaoMapJavascriptKey = kakaoMapJavascriptKey
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "\\n")
val defaultApiBaseUrl = System.getenv("MOAGIL_API_BASE_URL").orEmpty().trim()
    .ifBlank { "https://moagil-eta.vercel.app" }
val escapedDefaultApiBaseUrl = defaultApiBaseUrl
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "\\n")
val kakaoMapOrigin = System.getenv("MOAGIL_KAKAO_MAP_ORIGIN").orEmpty().trim().trimEnd('/')
    .ifBlank { "https://moagil-eta.vercel.app" }
val escapedKakaoMapOrigin = kakaoMapOrigin
    .replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\r", "")
    .replace("\n", "\\n")

android {
    namespace = "kr.moagil.vehicle"
    compileSdk = 34

    defaultConfig {
        applicationId = "kr.moagil.vehicle"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "KAKAO_MAP_JAVASCRIPT_KEY", "\"$escapedKakaoMapJavascriptKey\"")
        buildConfigField("String", "DEFAULT_API_BASE_URL", "\"$escapedDefaultApiBaseUrl\"")
        buildConfigField("String", "KAKAO_MAP_ORIGIN", "\"$escapedKakaoMapOrigin\"")
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("moagilRelease") {
                storeFile = file(releaseSigningValues.getValue("storeFile"))
                storePassword = releaseSigningValues.getValue("storePassword")
                keyAlias = releaseSigningValues.getValue("keyAlias")
                keyPassword = releaseSigningValues.getValue("keyPassword")
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "x86_64")
            isUniversalApk = false
        }
    }

    flavorDimensions += "service"
    productFlavors {
        create("mock") {
            dimension = "service"
            applicationIdSuffix = ".mock"
            versionNameSuffix = "-mock"
            buildConfigField("boolean", "PLEOS_NATIVE_NAVIGATION", "false")
        }
        create("pleos") {
            dimension = "service"
            versionNameSuffix = "-pleos"
            buildConfigField("boolean", "PLEOS_NATIVE_NAVIGATION", "true")
        }
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartextTraffic"] = "true"
            if (System.getenv("MOAGIL_QA_APPLICATION_SUFFIX") == "true") {
                applicationIdSuffix = ".qa"
            }
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("moagilRelease")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    buildToolsVersion = "36.0.0"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testOptions { animationsDisabled = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.2")
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.zxing.core)
    add("pleosImplementation", "ai.pleos.playground:NaviHelper:3.0.5")
    add("pleosImplementation", "ai.pleos.playground:FusedLocation:1.1.3")
    val androidCarApi = android.sdkDirectory.resolve("platforms/android-34/optional/android.car.jar")
    if (androidCarApi.isFile) add("pleosCompileOnly", files(androidCarApi))

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
