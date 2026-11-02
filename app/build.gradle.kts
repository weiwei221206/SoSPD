import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}
val releaseCredentialsFile = rootProject.file(".signing/signing.properties")
val releaseCredentials = Properties().apply {
    if (releaseCredentialsFile.isFile) releaseCredentialsFile.inputStream().use { load(it) }
}
android {
    namespace = "io.github.sospd"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.sospd"
        minSdk = 29
        targetSdk = 36
        versionCode = 2
        versionName = "4.0.2-zhcn1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    androidResources.localeFilters += "zh"
    bundle.language.enableSplit = false
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (releaseCredentialsFile.isFile) {
            create("sospdRelease") {
                storeFile = rootProject.file(".signing/${releaseCredentials.getProperty("storeFile")}")
                storePassword = releaseCredentials.getProperty("storePassword")
                keyAlias = releaseCredentials.getProperty("keyAlias")
                keyPassword = releaseCredentials.getProperty("keyPassword")
                storeType = "PKCS12"
            }
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("sospdRelease")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources.excludes += setOf("META-INF/DEPENDENCIES", "META-INF/INDEX.LIST") }
}
dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
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
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(platform("androidx.compose:compose-bom:2026.06.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
}

