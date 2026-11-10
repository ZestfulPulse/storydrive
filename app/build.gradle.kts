plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.zestfulpulse.storydrive"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.zestfulpulse.storydrive"
        minSdk = 28
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    // 릴리스 서명: 값은 저장소가 아니라 사용자 홈의 ~/.gradle/gradle.properties에서 읽는다.
    // 네 값이 모두 있을 때만 서명 설정을 만든다 (없으면 assembleRelease는 서명 안 된 APK를 만든다).
    val releaseStoreFile = providers.gradleProperty("STORYDRIVE_RELEASE_STORE_FILE").orNull
    val releaseStorePassword = providers.gradleProperty("STORYDRIVE_RELEASE_STORE_PASSWORD").orNull
    val releaseKeyAlias = providers.gradleProperty("STORYDRIVE_RELEASE_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.gradleProperty("STORYDRIVE_RELEASE_KEY_PASSWORD").orNull
    val hasReleaseSigning = listOf(releaseStoreFile, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
        .all { !it.isNullOrBlank() }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.vehicle.sdk)
    implementation(libs.pleos.tts.sdk)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.junit)
}
