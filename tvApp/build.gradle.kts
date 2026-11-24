// «موج» للشاشة: تطبيق مستقل مكتوب من الصفر، خفيف ومصمم للريموت.
plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.mawj.tv"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mawj.tv"
        minSdk = 23
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("mawj.tv.versionCode").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("mawj.tv.versionName").orNull ?: "0.1"
        // TVs run 32-bit Android; one ABI keeps the APK small.
        ndk { abiFilters += listOf("armeabi-v7a") }
        val tmdb = providers.environmentVariable("TMDB_API_KEY").orNull.orEmpty()
        buildConfigField("String", "TMDB_KEY", "\"$tmdb\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    signingConfigs {
        getByName("debug") {
            val key = rootProject.file("androidApp/luxo-debug.keystore")
            if (key.exists()) {
                storeFile = key
                storePassword = "android"
                keyAlias = "androiddebugkey"
                keyPassword = "android"
            }
        }
    }

    buildTypes {
        getByName("release") {
            // R8: shrunk and optimised, the main reason Compose runs smoothly on weak TVs.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" }
        jniLibs { useLegacyPackaging = true }
    }

    androidResources {
        localeFilters += listOf("ar", "en")
    }
}

dependencies {
    implementation(libs.compose.runtime)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui)
    implementation(libs.androidx.activity.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.ktor3)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.mpv.android.lib)
    implementation("com.google.zxing:core:3.5.3")
}
