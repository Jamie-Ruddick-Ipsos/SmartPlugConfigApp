plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

android {
    namespace = "com.example.smartplugconfig"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.smartplugconfig"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.1"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
configurations.all {
    resolutionStrategy {
        force ("org.jetbrains:annotations:23.0.0")
        force ("androidx.compose.ui:ui-android:1.6.8")
    }
}


dependencies {
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
//    implementation(platform(libs.androidx.compose.bom))
//    implementation(libs.androidx.ui) {
//        exclude(group= "androidx.compose.ui", module= "ui-desktop")
//    }
//    implementation(libs.androidx.ui.graphics){
//        exclude(group= "androidx.compose.ui", module= "ui-desktop")
//    }
//    implementation(libs.androidx.ui.tooling.preview){
//        exclude (group= "androidx.compose.ui", module= "ui-desktop")
//    }
    implementation ("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.ui:ui")

    // Android Studio Preview support
    implementation("androidx.compose.ui:ui-tooling-preview")
    //debugImplementation("androidx.compose.ui:ui-tooling")
    implementation(libs.androidx.material3)
    //implementation(libs.androidx.media3.common)
    //implementation(libs.androidx.appcompat)
//    implementation(libs.androidx.ui.desktop){
//        exclude(group= "androidx.compose.ui", module= "ui-android")
//    }
    //implementation (platform("androidx.compose:compose-bom:2023.01.00"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.okhttp)
    implementation(libs.kmqtt.common.jvm)
    implementation(libs.kmqtt.client.jvm)
    implementation(libs.kmqtt.broker.jvm)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)


}
