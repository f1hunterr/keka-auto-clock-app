plugins {
    id("com.android.application")
}

android {
    namespace = "com.hemanth.kekaautoclock"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hemanth.kekaautoclock"
        minSdk = 26
        targetSdk = 34
        versionCode = 12
        versionName = "2.12"
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
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("com.google.android.gms:play-services-location:21.1.0")
    implementation("androidx.work:work-runtime:2.9.0")
}

configurations.all {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
}
