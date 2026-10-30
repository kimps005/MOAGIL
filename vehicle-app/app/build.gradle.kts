import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.isFile) propertiesFile.inputStream().use { this.load(it) }
}
val naverMapClientId = localProperties.getProperty("naver.map.clientId", "").trim()
val escapedNaverMapClientId = naverMapClientId.replace("\\", "\\\\").replace("\"", "\\\"")

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
        manifestPlaceholders["naverMapClientId"] = naverMapClientId
        buildConfigField("String", "NAVER_MAP_CLIENT_ID", "\"$escapedNaverMapClientId\"")
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
        debug { }
        release {
            isMinifyEnabled = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { animationsDisabled = true }
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
    implementation("com.naver.maps:map-sdk:3.24.0")
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
