plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("kotlin-parcelize")
}

android {
    namespace = "at.hcw.flaminco"

    // Wir nutzen 35 als 'Brille' für den Compiler, damit er die alten Ressourcen 
    // heute noch korrekt verarbeiten kann. Die App bleibt trotzdem 
    // strikt auf Android 8.0 begrenzt.
    compileSdk = 35

    defaultConfig {
        applicationId = "at.hcw.flaminco"

        minSdk = 26
        //noinspection ExpiredTargetSdkVersion
        targetSdk = 26
        maxSdk = 26

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_1_8)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
