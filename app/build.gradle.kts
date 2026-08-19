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
    versionCode = 2
    versionName = "1.6.1-offline"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    javaCompileOptions {
      annotationProcessorOptions {
        // Exports each @Database version's schema as JSON under app/schemas so Room migrations
        // can be tested against a real, compiler-verified schema instead of hand-guessed DDL
        // (agent-docs/analisis-implementacion-supabase-sync.md, hallazgo 6 / Fase 7.2). Historical
        // versions the app already shipped before this was turned on (1-17) don't have an exported
        // file from back when they were current, so MIGRATION_14_15's test supplies its own
        // hand-authored 14.json starting point (see androidTest/.../MigrationTest.kt) — this
        // setting only guarantees *future* versions are captured automatically from here on.
        arguments += mapOf("room.schemaLocation" to "$projectDir/schemas")
      }
    }
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

  sourceSets {
    // Room's MigrationTestHelper.createDatabase() reads exported schema JSON files as assets
    // (see 0.2 Fase 7.2 above) — this is where room.schemaLocation writes them.
    getByName("androidTest").assets.srcDirs("$projectDir/schemas")
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
  // MigrationTestHelper for the MIGRATION_14_15 instrumented test (Fase 7.2) — needs a real SQLite
  // driver, hence androidTest rather than a plain JVM unit test.
  androidTestImplementation("androidx.room:room-testing:2.6.1")
  
  // ViewPager2
  implementation("androidx.viewpager2:viewpager2:1.0.0")
}