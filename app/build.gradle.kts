plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.abdelhay.dhikr"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.abdelhay.dhikr"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // اللغة الافتراضية عربية مع دعم RTL
        resourceConfigurations += setOf("ar", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true   // java.time على API 24
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }

}

dependencies {
    val room = "2.6.1"

    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.navigation:navigation-compose:2.8.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")

    implementation("androidx.room:room-runtime:$room")
    implementation("androidx.room:room-ktx:$room")
    ksp("androidx.room:room-compiler:$room")

    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.core:core-ktx:1.13.1")

    // حساب مواقيت الصلاة محليًّا — بلا شبكة وبلا تبعيات
    implementation("com.batoulapps.adhan:adhan:1.2.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
