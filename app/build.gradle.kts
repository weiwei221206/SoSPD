plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "io.github.sospd"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.sospd"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources.localeFilters += "zh"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
}
dependencies {
    implementation(project(":engine"))
    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    annotationProcessor("androidx.room:room-compiler:2.7.2")
    implementation("com.google.code.gson:gson:2.13.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

