plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.dit.tunnel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.dit.tunnel"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // SSH client (fork JSch yang masih dirawat, mendukung algoritma modern)
    implementation("com.github.mwiede:jsch:0.2.19")
}
