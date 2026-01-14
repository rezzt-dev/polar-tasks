plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  id("kotlin-kapt")
  alias(libs.plugins.hilt.android)
}

android {
  namespace = "app.polar"
  compileSdk {
    version = release(36)
  }

  defaultConfig {
    applicationId = "app.polar"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  kotlinOptions {
    jvmTarget = "11"
  }
  buildFeatures {
    viewBinding = true
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.appcompat)
  implementation(libs.material)
  implementation(libs.androidx.activity)
  implementation(libs.androidx.constraintlayout)
  
  // Room Database
  implementation("androidx.room:room-runtime:2.6.1")
  implementation("androidx.room:room-ktx:2.6.1")
  kapt("androidx.room:room-compiler:2.6.1")
  
  // Lifecycle components
  implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.7.0")
  implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.7.0")
  implementation("androidx.fragment:fragment-ktx:1.6.2")
  
  // Coroutines
  // Coroutines
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

  // Hilt
  implementation(libs.hilt.android)
  kapt(libs.hilt.compiler)
  
  // Gson
  implementation("com.google.code.gson:gson:2.10.1")
  
  // WorkManager
  implementation("androidx.work:work-runtime-ktx:2.9.0")
  
  testImplementation(libs.junit)
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
  testImplementation("io.mockk:mockk:1.13.10")
  testImplementation("androidx.arch.core:core-testing:2.2.0")
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
}